package com.internlink.core.application.evaluation;

import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.domain.evaluation.AssessmentScheme;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.evaluation.dto.request.AssessmentSchemeRequest;
import com.internlink.core.presentation.evaluation.dto.response.AssessmentSchemeResponse;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssessmentSchemeService {
    private final JpaAssessmentSchemeRepository schemes;
    private final JpaInternshipTermRepository terms;
    private final JpaAcademicProgramRepository programs;
    private final JpaStudentRosterRepository rosters;
    private final JpaInternshipPlacementRepository placements;
    private final JpaUserRepository users;
    private final SecurityGuard security;
    private final com.internlink.core.application.system.notification
                    .PlacementWorkflowNotificationHelper
            notifications;

    @Transactional
    public AssessmentSchemeResponse create(AssessmentSchemeRequest request) {
        var term =
                terms.findById(request.termId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "InternshipTerm", "id", request.termId()));
        var program =
                programs.findById(request.programId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "AcademicProgram", "id", request.programId()));
        // Khoa chỉ xây dựng phương án đánh giá cho ngành thuộc khoa mình.
        ResourceAuthorization.require(
                ResourceAuthorization.managesDepartment(actor(), program.getDepartment().getId()));
        if (!"REGULAR".equals(program.getTrack()))
            throw new BadRequestException(
                    "Phạm vi này chỉ áp dụng chương trình đại trà, không gồm CTCLC");
        AssessmentRules.validate(request.components());
        if (!Boolean.TRUE.equals(request.requireFinalReport()))
            throw new BadRequestException(
                    "Báo cáo cuối kỳ là bắt buộc trong phạm vi quản lý thực tập này");
        if (Boolean.TRUE.equals(request.requireMidtermReport())
                        && request.midtermReportDueAt() == null
                || Boolean.TRUE.equals(request.requireFinalReport())
                        && request.finalReportDueAt() == null)
            throw new BadRequestException("Báo cáo bắt buộc phải có hạn nộp");
        if (request.midtermReportDueAt() != null
                && request.finalReportDueAt() != null
                && request.midtermReportDueAt().isAfter(request.finalReportDueAt()))
            throw new BadRequestException("Hạn báo cáo giữa kỳ phải trước hạn cuối kỳ");
        if (schemes.existsByTermIdAndProgramIdAndCohortCodeAndCourseCodeAndRevision(
                request.termId(),
                request.programId(),
                request.cohortCode(),
                request.courseCode(),
                request.revision()))
            throw new BadRequestException("Phiên bản phương án đánh giá đã tồn tại");
        var scheme =
                AssessmentScheme.builder()
                        .term(term)
                        .program(program)
                        .cohortCode(request.cohortCode().trim())
                        .courseCode(request.courseCode().trim())
                        .revision(request.revision())
                        .sourceReference(request.sourceReference().trim())
                        .components(request.components())
                        .requiredLogbookWeeks(request.requiredLogbookWeeks())
                        .weeklyGraceDays(request.weeklyGraceDays())
                        .requireMidtermReport(request.requireMidtermReport())
                        .requireFinalReport(request.requireFinalReport())
                        .midtermReportDueAt(request.midtermReportDueAt())
                        .finalReportDueAt(request.finalReportDueAt())
                        .status("DRAFT")
                        .build();
        var saved = schemes.save(scheme);
        if (!"DRAFT".equals(scheme.getStatus())) notifications.scheme(scheme, actor().getId());
        return response(saved);
    }

    @Transactional
    public AssessmentSchemeResponse approve(UUID id) {
        var scheme = get(id);
        ResourceAuthorization.require(
                ResourceAuthorization.managesDepartment(
                        actor(), scheme.getProgram().getDepartment().getId()));
        if (!"DRAFT".equals(scheme.getStatus()))
            throw new BadRequestException("Chỉ có thể duyệt phương án đang DRAFT");
        if (schemes.existsByTermIdAndProgramIdAndCohortCodeAndCourseCodeAndStatus(
                scheme.getTerm().getId(),
                scheme.getProgram().getId(),
                scheme.getCohortCode(),
                scheme.getCourseCode(),
                "APPROVED"))
            throw new BadRequestException("Học phần đã có phương án được duyệt trong kỳ này");
        AssessmentRules.validate(scheme.getComponents());
        scheme.setApprovedBy(actor());
        scheme.setApprovedAt(OffsetDateTime.now());
        scheme.setStatus("APPROVED");
        var saved = schemes.save(scheme);
        if (!"DRAFT".equals(scheme.getStatus())) notifications.scheme(scheme, actor().getId());
        return response(saved);
    }

    @Transactional
    public AssessmentSchemeResponse retire(UUID id) {
        var scheme = get(id);
        ResourceAuthorization.require(
                ResourceAuthorization.managesDepartment(
                        actor(), scheme.getProgram().getDepartment().getId()));
        if (!"APPROVED".equals(scheme.getStatus()))
            throw new BadRequestException("Chỉ có thể thay thế phương án đã duyệt");
        scheme.setStatus("RETIRED");
        var saved = schemes.save(scheme);
        if (!"DRAFT".equals(scheme.getStatus())) notifications.scheme(scheme, actor().getId());
        return response(saved);
    }

    @Transactional
    public AssessmentSchemeResponse bind(UUID placementId, UUID schemeId) {
        var placement =
                placements
                        .findById(placementId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "InternshipPlacement", "id", placementId));
        var scheme = get(schemeId);
        ResourceAuthorization.require(
                ResourceAuthorization.managesDepartment(
                        actor(), DepartmentScope.idOf(placement)));
        if (placement.getStatus() != PlacementStatus.PREPARING
                || placement.getAssessmentScheme() != null)
            throw new BadRequestException(
                    "Chỉ gán phương án một lần khi đợt thực tập đang PREPARING");
        if (!"APPROVED".equals(scheme.getStatus())
                || !scheme.getTerm().getId().equals(placement.getTerm().getId()))
            throw new BadRequestException("Phương án chưa duyệt hoặc khác kỳ thực tập");
        StudentRoster roster =
                rosters.findByTermIdAndClaimedUserId(
                                placement.getTerm().getId(), placement.getStudent().getId())
                        .orElseThrow(
                                () ->
                                        new BadRequestException(
                                                "Sinh viên chưa có danh sách được xác nhận trong"
                                                        + " kỳ"));
        if (!roster.getProgram().getId().equals(scheme.getProgram().getId())
                || !roster.getAcademicYear().equals(scheme.getCohortCode())
                || !scheme.getCourseCode().equalsIgnoreCase(roster.getInternshipCourseCode()))
            throw new BadRequestException(
                    "Phương án không khớp ngành, khóa hoặc mã học phần của sinh viên");
        long internshipDays =
                ChronoUnit.DAYS.between(placement.getStartDate(), placement.getEndDate()) + 1;
        if (scheme.getRequiredLogbookWeeks() > (internshipDays + 6) / 7)
            throw new BadRequestException("Số tuần nhật ký bắt buộc vượt quá thời gian thực tập");
        placement.setAssessmentScheme(scheme);
        placements.save(placement);
        notifications.changed(
                placement,
                actor().getId(),
                "ASSESSMENT_SCHEME_BOUND",
                "Đã gán phương án đánh giá thực tập");
        return response(scheme);
    }

    @Transactional(readOnly = true)
    public List<AssessmentSchemeResponse> byTerm(UUID termId) {
        terms.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        var actor = actor();
        ResourceAuthorization.require(ResourceAuthorization.worksOnTerm(actor));
        return schemes.findByTermId(termId).stream()
                .filter(sc -> ResourceAuthorization.managesDepartment(
                        actor, sc.getProgram().getDepartment().getId()))
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssessmentSchemeResponse forPlacement(UUID placementId) {
        var placement =
                placements
                        .findById(placementId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "InternshipPlacement", "id", placementId));
        ResourceAuthorization.require(ResourceAuthorization.canReadPlacement(actor(), placement));
        if (placement.getAssessmentScheme() == null)
            throw new BadRequestException("Lần thực tập chưa được gán phương án đánh giá");
        return response(placement.getAssessmentScheme());
    }

    private AssessmentScheme get(UUID id) {
        return schemes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AssessmentScheme", "id", id));
    }

    private com.internlink.core.domain.auth.User actor() {
        UUID id = security.currentUser().getId();
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private AssessmentSchemeResponse response(AssessmentScheme s) {
        return new AssessmentSchemeResponse(
                s.getId(),
                s.getTerm().getId(),
                s.getProgram().getId(),
                s.getCohortCode(),
                s.getCourseCode(),
                s.getRevision(),
                s.getSourceReference(),
                s.getComponents(),
                s.getRequiredLogbookWeeks(),
                s.getWeeklyGraceDays(),
                s.getRequireMidtermReport(),
                s.getRequireFinalReport(),
                s.getMidtermReportDueAt(),
                s.getFinalReportDueAt(),
                s.getStatus());
    }
}
