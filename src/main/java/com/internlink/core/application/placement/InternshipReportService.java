package com.internlink.core.application.placement;

import com.internlink.core.domain.placement.InternshipReport;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.placement.dto.response.InternshipReportResponse;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternshipReportService {
    private final JpaInternshipReportRepository reports;
    private final JpaInternshipPlacementRepository placements;
    private final JpaDocumentRepository documents;
    private final JpaUserRepository users;
    private final SecurityGuard security;
    private final com.internlink.core.application.system.notification
                    .PlacementWorkflowNotificationHelper
            notifications;

    @Transactional
    public InternshipReportResponse submit(UUID placementId, String type, UUID documentId) {
        var placement =
                placements
                        .findById(placementId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "InternshipPlacement", "id", placementId));
        TermGuard.requireNotClosed(placement.getTerm());
        var student = actor();
        ResourceAuthorization.require(
                student.getRole() == UserRole.STUDENT
                        && student.getId().equals(placement.getStudent().getId()));
        if (placement.getStatus() != PlacementStatus.ACTIVE
                || !List.of("MIDTERM", "FINAL").contains(type))
            throw new BadRequestException("Loại báo cáo hoặc trạng thái thực tập không hợp lệ");
        Document document =
                documents
                        .findById(documentId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Document", "id", documentId));
        if (document.getContextType() != ContextType.PLACEMENT
                || !document.getContextId().equals(placementId)
                || document.getDocumentType() != DocumentType.REPORT
                || !document.getOwner().getId().equals(student.getId())
                || !"ACTIVE".equals(document.getStatus()))
            throw new BadRequestException("Tệp báo cáo không hợp lệ hoặc không thuộc lần thực tập");
        var report =
                reports.findByPlacementIdAndReportType(placementId, type)
                        .orElse(
                                InternshipReport.builder()
                                        .placement(placement)
                                        .reportType(type)
                                        .build());
        // Nộp lại được khi GVHD chưa xét (thay file) hoặc khi được yêu cầu sửa; đã duyệt thì khóa.
        if (report.getId() != null && "APPROVED".equals(report.getStatus()))
            throw new BadRequestException("Báo cáo đã được GVHD duyệt, không thể nộp lại");
        report.setDocument(document);
        report.setStatus("SUBMITTED");
        report.setSubmittedAt(OffsetDateTime.now());
        OffsetDateTime dueAt = ReportDeadlines.due(placement, type);
        report.setWasLate(dueAt != null && report.getSubmittedAt().isAfter(dueAt));
        report.setLecturerFeedback(null);
        report.setReviewedBy(null);
        report.setReviewedAt(null);
        var saved = reports.save(report);
        notifications.report(
                report.getPlacement(), actor().getId(), "SUBMITTED".equals(report.getStatus()));
        return response(saved);
    }

    @Transactional
    public InternshipReportResponse review(UUID id, String decision, String feedback) {
        InternshipReport report =
                reports.findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("InternshipReport", "id", id));
        TermGuard.requireNotClosed(report.getPlacement().getTerm());
        var lecturer = actor();
        ResourceAuthorization.require(
                lecturer.getRole() == UserRole.LECTURER
                        && lecturer.getId().equals(report.getPlacement().getLecturer().getId()));
        if (!"SUBMITTED".equals(report.getStatus())
                || !List.of("APPROVED", "REVISION_REQUIRED").contains(decision))
            throw new BadRequestException("Trạng thái xét báo cáo không hợp lệ");
        if ("REVISION_REQUIRED".equals(decision) && (feedback == null || feedback.isBlank()))
            throw new BadRequestException("Cần nêu nội dung cần sửa");
        report.setStatus(decision);
        report.setLecturerFeedback(feedback != null ? feedback.trim() : null);
        report.setReviewedBy(lecturer);
        report.setReviewedAt(OffsetDateTime.now());
        var saved = reports.save(report);
        notifications.report(
                report.getPlacement(), actor().getId(), "SUBMITTED".equals(report.getStatus()));
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<InternshipReportResponse> byPlacement(UUID placementId) {
        var placement =
                placements
                        .findById(placementId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "InternshipPlacement", "id", placementId));
        ResourceAuthorization.require(ResourceAuthorization.canReadPlacement(actor(), placement));
        return reports.findByPlacementId(placementId).stream().map(this::response).toList();
    }

    private com.internlink.core.domain.auth.User actor() {
        UUID id = security.currentUser().getId();
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private InternshipReportResponse response(InternshipReport r) {
        return new InternshipReportResponse(
                r.getId(),
                r.getPlacement().getId(),
                r.getReportType(),
                r.getDocument().getId(),
                r.getStatus(),
                r.getWasLate(),
                r.getLecturerFeedback(),
                r.getSubmittedAt(),
                r.getReviewedAt(),
                r.getDocument().getOriginalName(),
                r.getDocument().getSizeBytes());
    }
}
