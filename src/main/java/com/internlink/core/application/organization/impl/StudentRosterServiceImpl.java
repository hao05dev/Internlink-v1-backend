package com.internlink.core.application.organization.impl;

import com.internlink.core.application.organization.TermDepartmentScope;
import com.internlink.core.application.organization.RosterImportNotifier;
import com.internlink.core.application.organization.RosterImportRules;
import com.internlink.core.presentation.organization.dto.response.RosterImportResponse;
import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.application.organization.StudentRosterService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.infrastructure.persistence.jpa.JpaAcademicProgramRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.organization.dto.request.StudentRosterImportItem;
import com.internlink.core.presentation.organization.dto.response.BatchProvisionResponse;
import com.internlink.core.presentation.organization.dto.response.StudentRosterResponse;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentRosterServiceImpl implements StudentRosterService {


    private final JpaStudentRosterRepository rosterRepository;
    private final JpaInternshipTermRepository termRepository;
    private final JpaAcademicProgramRepository programRepository;
    private final JpaUserRepository userRepository;
    private final SecurityGuard securityGuard;
    private final AuditLogService auditLogService;
    private final StudentRosterMapper mapper;
    private final StudentRosterAccountProvisioner accountProvisioner;
    private final RosterAttemptHistoryResolver attemptHistory;
    private final TermDepartmentScope termScope;
    private final RosterImportNotifier importNotifier;

    @Override
    @Transactional(readOnly = true)
    public List<StudentRosterResponse> getRostersByTerm(UUID termId) {
        InternshipTerm term = termRepository.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        var actor = userRepository.findById(securityGuard.currentUser().getId()).orElseThrow();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(actor) || ResourceAuthorization.worksOnTerm(actor)
                || (actor.getRole() == UserRole.LECTURER && actor.getDepartment() != null));
        List<StudentRoster> rosters = termScope.rosters(termId, actor);
        return withAttemptHistory(term, rosters);
    }

    @Override
    @Transactional
    public RosterImportResponse importRosterList(UUID termId, List<StudentRosterImportItem> items) {
        InternshipTerm term = termRepository.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        TermGuard.requireNotClosed(term);
        var actor = userRepository.findById(securityGuard.currentUser().getId()).orElseThrow();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(actor) || ResourceAuthorization.worksOnTerm(actor));
        // null = VP Trường / Admin nhập cho cả Trường; còn lại là khoa của Quản lý Khoa.
        UUID actorDepartment = termScope.restrictTo(actor);

        if (items.isEmpty()) {
            throw new BadRequestException("Danh sách import trống");
        }

        List<StudentRoster> rostersToSave = new ArrayList<>();
        List<RosterImportResponse.SkippedRow> skipped = new ArrayList<>();
        int created = 0;

        for (StudentRosterImportItem item : items) {
            RosterRowWriter.validate(item);
            AcademicProgram program = programRepository.findById(item.getProgramId())
                    .orElseThrow(() -> new ResourceNotFoundException("AcademicProgram", "id", item.getProgramId()));

            // Khoa chỉ bổ sung SV thuộc ngành của khoa mình; VP Trường nhập mọi ngành.
            if (actorDepartment != null && !actorDepartment.equals(program.getDepartment().getId())) {
                throw new BadRequestException("Ngành " + program.getCode() + " không thuộc khoa của bạn");
            }

            String studentCode = RosterRowWriter.studentCode(item);
            StudentRoster existing = rosterRepository.findByTermIdAndStudentCode(termId, studentCode).orElse(null);
            switch (RosterImportRules.decide(actorDepartment, existing)) {
                case SKIP_OTHER_DEPARTMENT -> skipped.add(new RosterImportResponse.SkippedRow(studentCode,
                        "Sinh viên đang thuộc khoa khác trong danh sách của Văn phòng Trường"));
                case LECTURER_ONLY -> {
                    if (RosterRowWriter.writeLecturer(existing, item, userRepository::findByEmail)) {
                        rostersToSave.add(existing);
                    }
                    skipped.add(new RosterImportResponse.SkippedRow(studentCode,
                            "Đã có trong danh sách của Văn phòng Trường: giữ nguyên hồ sơ, chỉ cập nhật GVHD (nếu có)"));
                }
                case CREATE, UPDATE -> {
                    StudentRoster roster = existing != null ? existing
                            : StudentRoster.builder().term(term).studentCode(studentCode).build();
                    if (existing == null) {
                        created++;
                    }
                    if (existing == null || actorDepartment == null) {
                        // VP Trường nhập lại thì dòng thành dòng chuẩn, kể cả khi trước đó Khoa bổ sung.
                        roster.setSource(RosterImportRules.sourceFor(actorDepartment));
                        roster.setAddedBy(actor);
                    }
                    RosterRowWriter.writeProfile(roster, item, program);
                    RosterRowWriter.writeLecturer(roster, item, userRepository::findByEmail);
                    rostersToSave.add(roster);
                }
            }
        }

        List<StudentRoster> saved = rosterRepository.saveAll(rostersToSave);
        List<StudentRosterResponse> responses = withAttemptHistory(term, saved);
        long repeatCount = responses.stream().filter(r -> r.getAttemptNumber() > 1).count();
        auditLogService.logAction(
                securityGuard.currentUser().getId(), "IMPORT_STUDENT_ROSTER", "InternshipTerm", termId,
                "SUCCESS", Map.of("count", saved.size(), "created", created, "skipped", skipped.size(),
                        "repeatCount", repeatCount, "source", RosterImportRules.sourceFor(actorDepartment).name()), null);
        if (actorDepartment == null) {
            importNotifier.schoolOfficeImported(term, saved);
        } else {
            importNotifier.facultyAdded(term, actor, created);
        }
        return new RosterImportResponse(created, saved.size() - created, responses, skipped);
    }

    @Override
    @Transactional
    public StudentRosterResponse provisionAccount(UUID rosterId) {
        StudentRoster roster = rosterRepository.findById(rosterId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentRoster", "id", rosterId));
        TermGuard.requireNotClosed(roster.getTerm());
        var actor = userRepository.findById(securityGuard.currentUser().getId()).orElseThrow();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(actor)
                || ResourceAuthorization.managesDepartment(actor, DepartmentScope.idOf(roster)));

        return accountProvisioner.provisionSingleAccount(roster, mapper);
    }

    @Override
    @Transactional
    public BatchProvisionResponse batchProvisionEligibleAccounts(UUID termId, List<UUID> rosterIds) {
        InternshipTerm term = termRepository.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        TermGuard.requireNotClosed(term);
        var actor = userRepository.findById(securityGuard.currentUser().getId()).orElseThrow();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(actor) || ResourceAuthorization.worksOnTerm(actor));

        return accountProvisioner.batchProvisionEligible(
                term, actor.getId(), termScope.rosters(termId, actor), rosterIds);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentRosterResponse getMySupervisionInfo(UUID studentUserId, String email) {
        StudentRoster roster = rosterRepository.findFirstByClaimedUserIdOrderByCreatedAtDesc(studentUserId)
                .or(() -> rosterRepository.findFirstByOfficialEmailOrderByCreatedAtDesc(email))
                .orElse(null);
        if (roster == null) {
            return null;
        }
        return mapper.mapToResponse(roster);
    }

    private List<StudentRosterResponse> withAttemptHistory(InternshipTerm term, List<StudentRoster> rosters) {
        List<StudentRosterResponse> responses = rosters.stream().map(mapper::mapToResponse).toList();
        attemptHistory.enrich(term, rosters, responses);
        return responses;
    }
}
