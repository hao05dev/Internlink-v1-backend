package com.internlink.core.application.placement.dossier;

import com.internlink.core.application.placement.InternshipReportService;
import com.internlink.core.application.system.notification.PlacementWorkflowNotificationHelper;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.InternshipReport;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.JpaDocumentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipReportRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.placement.dto.response.StudentDossierResponse;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** GVHD xem và duyệt từng file trong bộ hồ sơ nộp Khoa của sinh viên mình hướng dẫn. */
@Service
@RequiredArgsConstructor
public class PlacementDossierReviewService {

    private static final List<String> DECISIONS = List.of(DossierEntry.APPROVED, DossierEntry.REVISION_REQUIRED);

    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaDocumentRepository documentRepository;
    private final JpaInternshipReportRepository reportRepository;
    private final JpaUserRepository userRepository;
    private final SecurityGuard securityGuard;
    private final DossierInspector inspector;
    private final DossierResponseMapper responseMapper;
    private final InternshipReportService reportService;
    private final PlacementWorkflowNotificationHelper notifications;

    @Transactional(readOnly = true)
    public StudentDossierResponse getDossier(UUID placementId) {
        InternshipPlacement placement = placement(placementId);
        ResourceAuthorization.require(ResourceAuthorization.canReadPlacement(actor(), placement));
        return responseMapper.toResponse(placement, inspector.inspect(placement));
    }

    @Transactional
    public StudentDossierResponse review(UUID placementId, UUID documentId, String decision, String note) {
        InternshipPlacement placement = placement(placementId);
        TermGuard.requireNotClosed(placement.getTerm());
        User lecturer = actor();
        ResourceAuthorization.require(ResourceAuthorization.supervises(lecturer, placement));

        String normalized = decision != null ? decision.trim().toUpperCase() : "";
        if (!DECISIONS.contains(normalized)) {
            throw new BadRequestException("Kết quả duyệt chỉ có thể là APPROVED hoặc REVISION_REQUIRED");
        }
        String trimmedNote = note != null && !note.isBlank() ? note.trim() : null;
        if (DossierEntry.REVISION_REQUIRED.equals(normalized) && trimmedNote == null) {
            throw new BadRequestException("Cần nêu lý do yêu cầu nộp lại");
        }

        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));
        if (document.getContextType() != ContextType.PLACEMENT || !placementId.equals(document.getContextId())
            || !"ACTIVE".equals(document.getStatus())) {
            throw new BadRequestException("Tệp không thuộc hồ sơ của lần thực tập này");
        }
        DossierItem item = DossierItem.ofDocumentType(document.getDocumentType())
            .filter(DossierItem::mandatory)
            .orElseThrow(() -> new BadRequestException("Tệp này không phải biểu mẫu trong hồ sơ nộp Khoa"));

        DossierEntry current = inspector.inspect(placement).entry(item).orElseThrow();
        if (current.approvedByOffice()) {
            throw new BadRequestException("Mẫu 04 đã được Văn phòng nhận bản giấy, không cần duyệt lại");
        }
        if (current.document() == null || !documentId.equals(current.document().getId())) {
            throw new BadRequestException("Chỉ duyệt được bản nộp mới nhất của " + item.shortLabel());
        }

        if (item == DossierItem.M05) {
            // Báo cáo cuối kỳ dùng chung luồng duyệt báo cáo (luồng đó đã gửi thông báo cho sinh viên).
            InternshipReport report = reportRepository.findByPlacementIdAndReportType(placementId, "FINAL")
                .orElseThrow(() -> new BadRequestException("Chưa có báo cáo cuối kỳ để duyệt"));
            reportService.review(report.getId(), normalized, trimmedNote);
        } else {
            document.setReviewStatus(normalized);
            document.setReviewNote(trimmedNote);
            document.setReviewedBy(lecturer);
            document.setReviewedAt(OffsetDateTime.now());
            documentRepository.save(document);
            notifications.dossierReviewed(placement, lecturer.getId(), item.shortLabel(),
                DossierEntry.APPROVED.equals(normalized), trimmedNote);
        }
        return responseMapper.toResponse(placement, inspector.inspect(placement));
    }

    private InternshipPlacement placement(UUID placementId) {
        return placementRepository.findById(placementId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipPlacement", "id", placementId));
    }

    private User actor() {
        UUID id = securityGuard.currentUser().getId();
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }
}
