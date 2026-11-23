package com.internlink.core.application.evaluation;

import com.internlink.core.domain.evaluation.AssessmentComponentScore;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.evaluation.dto.request.ComponentScoreRequest;
import com.internlink.core.presentation.evaluation.dto.response.ComponentScoreResponse;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComponentScoreService {
    private final JpaAssessmentComponentScoreRepository scores;
    private final JpaInternshipPlacementRepository placements;
    private final JpaDocumentRepository documents;
    private final JpaUserRepository users;
    private final SecurityGuard security;
    private final com.internlink.core.application.placement.dossier.DossierInspector dossierInspector;
    private final com.internlink.core.application.system.notification
                    .PlacementWorkflowNotificationHelper
            notifications;

    @Transactional
    public ComponentScoreResponse submit(ComponentScoreRequest request) {
        var placement =
                placements
                        .findById(request.placementId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "InternshipPlacement",
                                                "id",
                                                request.placementId()));
        var actor = actor();
        if (placement.getAssessmentScheme() == null
                || !List.of("APPROVED", "RETIRED")
                        .contains(placement.getAssessmentScheme().getStatus()))
            throw new BadRequestException("Lần thực tập chưa có phương án đánh giá được duyệt");
        if (placement.getStatus() != PlacementStatus.ACTIVE && placement.getStatus() != PlacementStatus.COMPLETED)
            throw new BadRequestException("Chỉ chấm khi lần thực tập đang ACTIVE hoặc COMPLETED");
        Map<String, Object> definition =
                AssessmentRules.component(
                        placement.getAssessmentScheme().getComponents(), request.componentCode());
        String role = String.valueOf(definition.get("assessorRole"));
        String source = request.source().toUpperCase();
        if (!List.of("ONLINE", "OFFLINE").contains(source))
            throw new BadRequestException("Nguồn điểm không hợp lệ");
        if ("ONLINE".equals(source)) {
            ResourceAuthorization.require(
                    ("COMPANY_MENTOR".equals(role)
                                    && actor.getRole() == UserRole.COMPANY_MENTOR
                                    && placement.getMentor() != null
                                    && actor.getId().equals(placement.getMentor().getId()))
                            || ("LECTURER".equals(role)
                                    && actor.getRole() == UserRole.LECTURER
                                    && actor.getId().equals(placement.getLecturer().getId())));
        } else {
            // Phiếu M-TT-03 giấy niêm phong áp dụng cho mọi nơi thực tập; GVHD nhập thay cơ quan.
            ResourceAuthorization.require(
                    "COMPANY_MENTOR".equals(role)
                            && actor.getRole() == UserRole.LECTURER
                            && actor.getId().equals(placement.getLecturer().getId()));
        }
        if ("LECTURER".equals(role)) {
            // Điểm của GVHD chỉ nhập sau khi hồ sơ nộp Khoa đã đủ và được duyệt.
            dossierInspector.requireComplete(placement);
        }
        var existing =
                scores.findByPlacementIdAndComponentCode(
                        request.placementId(), request.componentCode());
        if (existing.isPresent()
                && !("OFFLINE".equals(source)
                        && "OFFLINE".equals(existing.get().getSource())
                        && "PENDING_VERIFICATION".equals(existing.get().getStatus())
                        && actor.getId().equals(existing.get().getSubmittedBy().getId())))
            throw new BadRequestException(
                    "Điểm đã xác minh hoặc đã công bố không thể sửa trực tiếp");
        Document evidence = null;
        if (request.evidenceDocumentId() != null) {
            evidence =
                    documents
                            .findById(request.evidenceDocumentId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Document",
                                                    "id",
                                                    request.evidenceDocumentId()));
            if (evidence.getContextType() != ContextType.PLACEMENT
                    || !evidence.getContextId().equals(placement.getId())
                    || !"ACTIVE".equals(evidence.getStatus()))
                throw new BadRequestException(
                        "Minh chứng không thuộc lần thực tập hoặc chưa hợp lệ");
        }
        if ("OFFLINE".equals(source)
                && evidence != null
                && evidence.getDocumentType() != DocumentType.EXTERNAL_EVALUATION)
            throw new BadRequestException(
                    "Minh chứng ngoại tuyến phải là phiếu đánh giá nơi thực tập");
        if (Boolean.TRUE.equals(definition.get("evidenceRequired")) && evidence == null)
            throw new BadRequestException("Thành phần này bắt buộc có minh chứng");
        if ("OFFLINE".equals(source) && evidence == null)
            throw new BadRequestException("Phiếu đánh giá ngoại tuyến phải có bản gốc/scan");
        var score =
                AssessmentRules.componentScore(
                        definition, request.score(), request.criteriaScores());
        var record =
                existing.orElseGet(
                        () ->
                                AssessmentComponentScore.builder()
                                        .placement(placement)
                                        .componentCode(request.componentCode())
                                        .submittedBy(actor)
                                        .build());
        record.setScore(score);
        record.setCriteriaScores(
                request.criteriaScores() != null ? request.criteriaScores() : Map.of());
        record.setSource(source);
        record.setEvidenceDocument(evidence);
        record.setStatus("OFFLINE".equals(source) ? "PENDING_VERIFICATION" : "VERIFIED");
        record.setVerifiedBy("ONLINE".equals(source) ? actor : null);
        record.setVerifiedAt("ONLINE".equals(source) ? OffsetDateTime.now() : null);
        var saved = scores.save(record);
        if ("OFFLINE".equals(source)) notifications.evidence(placement, actor.getId(), true);
        return response(saved);
    }

    @Transactional
    public ComponentScoreResponse verify(UUID id) {
        var score =
                scores.findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "AssessmentComponentScore", "id", id));
        var actor = actor();
        ResourceAuthorization.require(
                actor.getRole() == UserRole.LECTURER
                        && actor.getId().equals(score.getPlacement().getLecturer().getId()));
        if (!"OFFLINE".equals(score.getSource())
                || !"PENDING_VERIFICATION".equals(score.getStatus())
                || score.getEvidenceDocument() == null
                || !"ACTIVE".equals(score.getEvidenceDocument().getStatus()))
            throw new BadRequestException("Phiếu ngoại tuyến chưa đủ điều kiện xác minh");
        score.setStatus("VERIFIED");
        score.setVerifiedBy(actor);
        score.setVerifiedAt(OffsetDateTime.now());
        var saved = scores.save(score);
        notifications.evidence(score.getPlacement(), actor.getId(), false);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<ComponentScoreResponse> byPlacement(UUID placementId) {
        var placement =
                placements
                        .findById(placementId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "InternshipPlacement", "id", placementId));
        var actor = actor();
        ResourceAuthorization.require(
                actor.getRole() != UserRole.STUDENT
                        && ResourceAuthorization.canReadPlacement(actor, placement));
        return scores.findByPlacementId(placementId).stream().map(this::response).toList();
    }

    private com.internlink.core.domain.auth.User actor() {
        UUID id = security.currentUser().getId();
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private ComponentScoreResponse response(AssessmentComponentScore s) {
        return new ComponentScoreResponse(
                s.getId(),
                s.getPlacement().getId(),
                s.getComponentCode(),
                s.getScore(),
                s.getCriteriaScores(),
                s.getSource(),
                s.getEvidenceDocument() != null ? s.getEvidenceDocument().getId() : null,
                s.getStatus(),
                s.getSubmittedBy().getId(),
                s.getVerifiedBy() != null ? s.getVerifiedBy().getId() : null);
    }
}
