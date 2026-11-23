package com.internlink.core.application.evaluation.scan;

import com.internlink.core.application.evaluation.ComponentScoreService;
import com.internlink.core.application.evaluation.PaperEvaluationService;
import com.internlink.core.application.placement.dossier.DossierEntry;
import com.internlink.core.application.placement.dossier.DossierItem;
import com.internlink.core.application.system.DocumentService;
import com.internlink.core.application.system.notification.PlacementWorkflowNotificationHelper;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.evaluation.PaperEvaluationScan;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.evaluation.dto.request.ComponentScoreRequest;
import com.internlink.core.presentation.evaluation.dto.request.PaperEvaluationOverrideRequest;
import com.internlink.core.presentation.evaluation.dto.request.PaperScanConfirmRequest;
import com.internlink.core.presentation.evaluation.dto.response.PaperScanResponse;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * GVHD chụp Phiếu đánh giá của cơ quan (M-TT-03) → AI đọc điểm → GVHD soát, sửa rồi xác nhận.
 * Điểm chỉ ghi vào thành phần "cơ quan" của phương án đánh giá sau bước xác nhận.
 */
@Service
@RequiredArgsConstructor
public class PaperScanService {
    private static final Set<String> MIME = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "application/pdf");
    private static final Map<String, String> AI_ERRORS = Map.of(
            "form_reader_disabled", "Chức năng đọc phiếu bằng AI chưa được bật.",
            "unsupported_document_type", "Chỉ đọc được ảnh JPG, PNG, WEBP, HEIC hoặc PDF.",
            "document_too_large", "Ảnh quá lớn (tối đa 10 MB).");

    private final JpaPaperEvaluationScanRepository scans;
    private final JpaInternshipPlacementRepository placements;
    private final JpaDocumentRepository documents;
    private final JpaUserRepository users;
    private final JpaStudentProfileRepository studentProfiles;
    private final SecurityGuard security;
    private final DocumentService documentService;
    private final AiServiceClient ai;
    private final ComponentScoreService componentScores;
    private final PaperEvaluationService paperEvaluations;
    private final PlacementWorkflowNotificationHelper notifications;

    @Transactional
    public PaperScanResponse scan(UUID placementId, UUID documentId) {
        User actor = actor();
        InternshipPlacement placement = lecturerPlacement(placementId, actor);
        Document document = documents.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));
        if (document.getContextType() != ContextType.PLACEMENT || !placementId.equals(document.getContextId())
                || !"ACTIVE".equals(document.getStatus()) || document.getDocumentType() != DocumentType.EXTERNAL_EVALUATION)
            throw new BadRequestException("Ảnh phiếu phải là phiếu đánh giá của lần thực tập này");
        String mime = document.getMimeType() == null ? "" : document.getMimeType().toLowerCase(Locale.ROOT);
        if (!MIME.contains(mime)) throw new BadRequestException(AI_ERRORS.get("unsupported_document_type"));

        Map<String, Object> reading = ai.readM03(mime, Base64.getEncoder().encodeToString(bytes(documentId, actor)));
        if ("FAILED".equals(reading.get("status")))
            throw new BadRequestException(AI_ERRORS.getOrDefault(String.valueOf(reading.get("error_code")),
                    "AI chưa đọc được phiếu lúc này, thầy/cô thử lại hoặc nhập tay."));

        String studentCode = studentProfiles.findByUserId(placement.getStudent().getId())
                .map(p -> p.getStudentCode()).orElse(null);
        var scan = PaperEvaluationScan.builder()
                .placement(placement).document(document).scannedBy(actor)
                .model(Objects.toString(reading.get("model"), null))
                .reading(reading)
                .warnings(PaperScanRules.warnings(reading, placement.getStudent().getFullName(), studentCode))
                .suggestedScore(PaperScanRules.average(PaperScanRules.criteriaScores(reading).values()))
                .build();
        return response(scans.save(scan));
    }

    @Transactional
    public PaperScanResponse confirm(UUID scanId, PaperScanConfirmRequest request) {
        User actor = actor();
        PaperEvaluationScan scan = scans.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("PaperEvaluationScan", "id", scanId));
        InternshipPlacement placement = lecturerPlacement(scan.getPlacement().getId(), actor);
        if (scan.getConfirmedAt() != null) throw new BadRequestException("Phiếu này đã được xác nhận");

        Map<String, BigDecimal> criteria = criteria(request.criteriaScores());
        BigDecimal score = PaperScanRules.average(criteria.values());
        if (score == null) throw new BadRequestException("Cần ít nhất một tiêu chí có điểm");
        String componentCode = companyComponent(placement);
        if (componentCode != null) {
            Map<String, BigDecimal> present = new LinkedHashMap<>();
            criteria.forEach((code, value) -> { if (value != null) present.put(code, value); });
            var saved = componentScores.submit(new ComponentScoreRequest(placement.getId(), componentCode, score,
                    present, scan.getDocument().getId(), "OFFLINE"));
            componentScores.verify(saved.id());
        } else {
            paperEvaluations.savePaperEvaluation(actor.getId(), PaperEvaluationOverrideRequest.builder()
                    .placementId(placement.getId()).mentorScore(score)
                    .mentorFeedback(blank(request.comments()) ? "Số hóa từ Phiếu đánh giá M-TT-03" : request.comments().trim())
                    .evidenceDocumentId(scan.getDocument().getId()).build());
        }

        Map<String, Object> confirmed = new LinkedHashMap<>();
        confirmed.put("criteria", criteria);
        confirmed.put("comments", blank(request.comments()) ? null : request.comments().trim());
        confirmed.put("ctdtOptions", request.ctdtOptions() == null ? List.of()
                : request.ctdtOptions().stream().filter(PaperScanRules.CTDT_OPTIONS::contains).distinct().toList());
        confirmed.put("ctdtSuggestion", blank(request.ctdtSuggestion()) ? null : request.ctdtSuggestion().trim());
        confirmed.put("componentCode", componentCode);
        scan.setConfirmed(confirmed);
        scan.setConfirmedScore(score);
        scan.setEditedFields(PaperScanRules.editedFields(PaperScanRules.criteriaScores(scan.getReading()), criteria));
        scan.setConfirmedAt(OffsetDateTime.now());
        approveDossierCopy(placement, scan.getDocument(), actor);
        return response(scans.save(scan));
    }

    @Transactional(readOnly = true)
    public Optional<PaperScanResponse> latest(UUID placementId) {
        lecturerPlacement(placementId, actor());
        return scans.findFirstByPlacementIdOrderByCreatedAtDesc(placementId).map(this::response);
    }

    /** GVHD đã soát phiếu khi xác nhận điểm nên M03 trong hồ sơ nộp Khoa được duyệt luôn, không phải duyệt lần hai. */
    private void approveDossierCopy(InternshipPlacement placement, Document document, User lecturer) {
        if (!"ACTIVE".equals(document.getStatus()) || DossierEntry.APPROVED.equals(document.getReviewStatus())) return;
        document.setReviewStatus(DossierEntry.APPROVED);
        document.setReviewNote(null);
        document.setReviewedBy(lecturer);
        document.setReviewedAt(OffsetDateTime.now());
        documents.save(document);
        notifications.dossierReviewed(placement, lecturer.getId(), DossierItem.M03.shortLabel(), true, null);
    }

    /** Thành phần do cơ quan chấm trong phương án đã duyệt; null nếu lần thực tập chưa áp phương án. */
    static String companyComponent(InternshipPlacement placement) {
        var scheme = placement.getAssessmentScheme();
        if (scheme == null || !List.of("APPROVED", "RETIRED").contains(scheme.getStatus())) return null;
        return scheme.getComponents().stream()
                .filter(c -> "COMPANY_MENTOR".equals(String.valueOf(c.get("assessorRole"))))
                .map(c -> String.valueOf(c.get("code"))).findFirst()
                .orElseThrow(() -> new BadRequestException("Phương án đánh giá không có thành phần do cơ quan chấm"));
    }

    private static Map<String, BigDecimal> criteria(Map<String, BigDecimal> input) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String code : PaperScanRules.M03_CODES) {
            BigDecimal value = input.get(code);
            if (value != null && (value.compareTo(BigDecimal.ONE) < 0 || value.compareTo(BigDecimal.TEN) > 0
                    || value.stripTrailingZeros().scale() > 1))
                throw new BadRequestException("Điểm tiêu chí " + code + " phải từ 1 đến 10, tối đa một chữ số thập phân");
            result.put(code, value);
        }
        return result;
    }

    private InternshipPlacement lecturerPlacement(UUID placementId, User actor) {
        InternshipPlacement placement = placements.findById(placementId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipPlacement", "id", placementId));
        ResourceAuthorization.require(actor.getRole() == UserRole.LECTURER && placement.getLecturer() != null
                && actor.getId().equals(placement.getLecturer().getId()));
        return placement;
    }

    private byte[] bytes(UUID documentId, User actor) {
        try (var in = documentService.downloadDocument(documentId, actor.getId()).getResource().getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Không đọc được tệp ảnh phiếu");
        }
    }

    private User actor() {
        UUID id = security.currentUser().getId();
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private PaperScanResponse response(PaperEvaluationScan s) {
        return new PaperScanResponse(s.getId(), s.getPlacement().getId(), s.getDocument().getId(), s.getModel(),
                s.getReading(), s.getWarnings(), s.getSuggestedScore(), s.getConfirmed(), s.getConfirmedScore(),
                s.getEditedFields(), s.getConfirmedAt(), s.getCreatedAt());
    }
}
