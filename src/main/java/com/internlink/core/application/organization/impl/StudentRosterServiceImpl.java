package com.internlink.core.application.organization.impl;

import com.internlink.core.domain.placement.InternshipDurationRule;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentRosterServiceImpl implements StudentRosterService {

    private static final BigDecimal MAX_GPA = new BigDecimal("4.0");

    private final JpaStudentRosterRepository rosterRepository;
    private final JpaInternshipTermRepository termRepository;
    private final JpaAcademicProgramRepository programRepository;
    private final JpaUserRepository userRepository;
    private final SecurityGuard securityGuard;
    private final AuditLogService auditLogService;
    private final StudentRosterMapper mapper;
    private final StudentRosterAccountProvisioner accountProvisioner;
    private final RosterAttemptHistoryResolver attemptHistory;

    @Override
    @Transactional(readOnly = true)
    public List<StudentRosterResponse> getRostersByTerm(UUID termId) {
        InternshipTerm term = termRepository.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        var actor = userRepository.findById(securityGuard.currentUser().getId()).orElseThrow();
        ResourceAuthorization.require(
                ResourceAuthorization.managesDepartment(actor, term.getDepartment().getId())
                        || (actor.getRole() == UserRole.LECTURER
                        && actor.getDepartment() != null
                        && actor.getDepartment().getId().equals(term.getDepartment().getId())));
        List<StudentRoster> rosters = rosterRepository.findByTermId(termId);
        return withAttemptHistory(term, rosters);
    }

    @Override
    @Transactional
    public List<StudentRosterResponse> importRosterList(UUID termId, List<StudentRosterImportItem> items) {
        InternshipTerm term = termRepository.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        TermGuard.requireNotClosed(term);
        ResourceAuthorization.require(
                ResourceAuthorization.managesDepartment(
                        userRepository.findById(securityGuard.currentUser().getId()).orElseThrow(),
                        term.getDepartment().getId()));

        if (items.isEmpty()) {
            throw new BadRequestException("Danh sách import trống");
        }

        List<StudentRoster> rostersToSave = new ArrayList<>();

        for (StudentRosterImportItem item : items) {
            validateImportItem(item);
            AcademicProgram program = programRepository.findById(item.getProgramId())
                    .orElseThrow(() -> new ResourceNotFoundException("AcademicProgram", "id", item.getProgramId()));

            if (!program.getDepartment().getId().equals(term.getDepartment().getId())) {
                throw new BadRequestException("Ngành học không thuộc khoa quản lý kỳ thực tập");
            }

            String cleanStudentCode = sanitizeString(item.getStudentCode()).toUpperCase();
            String cleanFullName = sanitizeString(item.getFullName());
            String cleanEmail = sanitizeString(item.getOfficialEmail()).toLowerCase();
            String cleanYear = sanitizeString(item.getAcademicYear());
            String cleanClass = sanitizeString(item.getClassCode());
            String cleanCourse = sanitizeString(item.getInternshipCourseCode()).toUpperCase();

            StudentRoster roster = rosterRepository
                    .findByTermIdAndStudentCode(termId, cleanStudentCode)
                    .orElse(StudentRoster.builder()
                            .term(term)
                            .studentCode(cleanStudentCode)
                            .build());

            roster.setProgram(program);
            roster.setOfficialEmail(cleanEmail);
            roster.setFullName(cleanFullName);
            roster.setAcademicYear(cleanYear);
            roster.setClassCode(cleanClass);
            roster.setInternshipCourseCode(cleanCourse);
            if (item.getInternshipCredits() != null) {
                InternshipDurationRule.requireValidCredits(item.getInternshipCredits());
                roster.setInternshipCredits(item.getInternshipCredits());
            }
            roster.setGpa(item.getGpa());
            if (item.getPhoneNumber() != null && !item.getPhoneNumber().isBlank()) {
                roster.setPhoneNumber(sanitizeString(item.getPhoneNumber()));
            }

            // Ghi nhận thông tin Giảng viên hướng dẫn học phần
            if (item.getLecturerCode() != null && !item.getLecturerCode().isBlank()) {
                roster.setLecturerCode(sanitizeString(item.getLecturerCode()));
            }
            if (item.getLecturerName() != null && !item.getLecturerName().isBlank()) {
                roster.setLecturerName(sanitizeString(item.getLecturerName()));
            }
            if (item.getLecturerEmail() != null && !item.getLecturerEmail().isBlank()) {
                String lecEmail = sanitizeString(item.getLecturerEmail()).toLowerCase();
                roster.setLecturerEmail(lecEmail);
                userRepository.findByEmail(lecEmail).ifPresent(roster::setAssignedLecturer);
            }

            rostersToSave.add(roster);
        }

        List<StudentRoster> saved = rosterRepository.saveAll(rostersToSave);
        List<StudentRosterResponse> responses = withAttemptHistory(term, saved);
        long repeatCount = responses.stream().filter(r -> r.getAttemptNumber() > 1).count();
        auditLogService.logAction(
                securityGuard.currentUser().getId(), "IMPORT_STUDENT_ROSTER", "InternshipTerm", termId,
                "SUCCESS", Map.of("count", saved.size(), "repeatCount", repeatCount), null);
        return responses;
    }

    @Override
    @Transactional
    public StudentRosterResponse provisionAccount(UUID rosterId) {
        StudentRoster roster = rosterRepository.findById(rosterId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentRoster", "id", rosterId));
        TermGuard.requireNotClosed(roster.getTerm());
        var actor = userRepository.findById(securityGuard.currentUser().getId()).orElseThrow();
        ResourceAuthorization.require(
                ResourceAuthorization.managesDepartment(actor, roster.getTerm().getDepartment().getId()));

        return accountProvisioner.provisionSingleAccount(roster, mapper);
    }

    @Override
    @Transactional
    public BatchProvisionResponse batchProvisionEligibleAccounts(UUID termId, List<UUID> rosterIds) {
        InternshipTerm term = termRepository.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        TermGuard.requireNotClosed(term);
        var actor = userRepository.findById(securityGuard.currentUser().getId()).orElseThrow();
        ResourceAuthorization.require(
                ResourceAuthorization.managesDepartment(actor, term.getDepartment().getId()));

        return accountProvisioner.batchProvisionEligible(term, actor.getId(), rosterIds);
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

    private String sanitizeString(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        while (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-")
                || trimmed.startsWith("@") || trimmed.startsWith("\t") || trimmed.startsWith("\r")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed;
    }

    private void validateImportItem(StudentRosterImportItem item) {
        if (item.getProgramId() == null
                || item.getStudentCode() == null || item.getStudentCode().isBlank()
                || item.getFullName() == null || item.getFullName().isBlank()
                || item.getOfficialEmail() == null || !item.getOfficialEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                || item.getAcademicYear() == null || item.getAcademicYear().isBlank()) {
            throw new BadRequestException("Dòng roster thiếu MSSV, họ tên, email, khóa hoặc ngành học");
        }
        if (item.getInternshipCourseCode() == null || item.getInternshipCourseCode().isBlank()) {
            throw new BadRequestException("Cần mã học phần thực tập theo chương trình đào tạo");
        }
        if (item.getGpa() == null
                || item.getGpa().compareTo(BigDecimal.ZERO) < 0
                || item.getGpa().compareTo(MAX_GPA) > 0) {
            throw new BadRequestException("Điểm GPA của sinh viên " + item.getStudentCode() + " phải nằm trong khoảng 0.0 - 4.0");
        }
    }
}
