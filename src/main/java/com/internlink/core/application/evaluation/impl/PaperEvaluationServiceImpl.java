package com.internlink.core.application.evaluation.impl;

import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.application.evaluation.PaperEvaluationService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.evaluation.AssessmentComponentScore;
import com.internlink.core.domain.evaluation.RubricEvaluation;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.PortfolioForm;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.evaluation.dto.request.PaperEvaluationOverrideRequest;
import com.internlink.core.presentation.evaluation.dto.response.RubricEvaluationResponse;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.RubricStage;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperEvaluationServiceImpl implements PaperEvaluationService {

    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaUserRepository userRepository;
    private final JpaRubricEvaluationRepository rubricRepository;
    private final JpaAssessmentComponentScoreRepository componentScoreRepository;
    private final JpaPortfolioFormRepository portfolioFormRepository;
    private final JpaDocumentRepository documentRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public RubricEvaluationResponse savePaperEvaluation(UUID actorUserId, PaperEvaluationOverrideRequest request) {
        InternshipPlacement placement = placementRepository.findById(request.getPlacementId())
            .orElseThrow(() -> new ResourceNotFoundException("InternshipPlacement", "id", request.getPlacementId()));
        TermGuard.requireNotClosed(placement.getTerm());

        User actor = userRepository.findById(actorUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));

        boolean isLecturer = actor.getRole() == UserRole.LECTURER
            && placement.getLecturer() != null
            && actor.getId().equals(placement.getLecturer().getId());
        boolean isFaculty = actor.getRole() == UserRole.FACULTY_ADMIN
            && ResourceAuthorization.managesDepartment(actor, DepartmentScope.idOf(placement));

        if (!isLecturer && !isFaculty) {
            throw new BadRequestException("Chỉ GVHD phụ trách hoặc Quản lý khoa mới được số hóa phiếu đánh giá cơ quan");
        }

        Document evidenceDoc = null;
        if (request.getEvidenceDocumentId() != null) {
            evidenceDoc = documentRepository.findById(request.getEvidenceDocumentId())
                .filter(d -> d.getContextType() == ContextType.PLACEMENT && d.getContextId().equals(placement.getId()))
                .orElse(null);
        }

        // 1. Cập nhật hoặc tạo RubricEvaluation của Cán bộ cơ quan
        User mentorUser = placement.getMentor() != null ? placement.getMentor() : actor;
        RubricEvaluation rubric = rubricRepository
            .findByPlacementIdAndEvaluatorIdAndEvaluationStage(placement.getId(), mentorUser.getId(), RubricStage.FINAL)
            .orElse(RubricEvaluation.builder()
                .placement(placement)
                .evaluator(mentorUser)
                .evaluatorRole(UserRole.COMPANY_MENTOR)
                .evaluationStage(RubricStage.FINAL)
                .build());

        rubric.setRubricVersion("SEALED-PAPER-OFFLINE");
        rubric.setFinalScore(request.getMentorScore());
        rubric.setQualitativeFeedback(request.getMentorFeedback());
        rubric.setStatus("SUBMITTED");
        rubric.setSubmittedAt(OffsetDateTime.now());
        rubric.setCriteriaScores(List.of(
            Map.of("criterionId", "OFFLINE_MENTOR_TOTAL", "criterionName", "Điểm đánh giá phiếu giấy cơ quan",
                "weight", 100, "score", request.getMentorScore(), "proofUrl", Objects.toString(request.getProofUrl(), ""))
        ));
        RubricEvaluation savedRubric = rubricRepository.save(rubric);

        // 2. Cập nhật AssessmentComponentScore nguồn OFFLINE
        AssessmentComponentScore compScore = componentScoreRepository
            .findByPlacementIdAndComponentCode(placement.getId(), "M03")
            .orElse(AssessmentComponentScore.builder()
                .placement(placement)
                .componentCode("M03")
                .submittedBy(actor)
                .build());

        compScore.setScore(request.getMentorScore());
        compScore.setSource("OFFLINE");
        compScore.setStatus("VERIFIED");
        compScore.setVerifiedBy(actor);
        compScore.setVerifiedAt(OffsetDateTime.now());
        if (evidenceDoc != null) {
            compScore.setEvidenceDocument(evidenceDoc);
        }
        compScore.setCriteriaScores(Map.of(
            "TOTAL", request.getMentorScore()
        ));
        componentScoreRepository.save(compScore);

        // 3. Đồng bộ form M03 sang CONFIRMED
        portfolioFormRepository.findByPlacementIdAndKind(placement.getId(), "M03").ifPresent(f -> {
            Map<String, Object> content = f.getContent() != null ? new HashMap<>(f.getContent()) : new HashMap<>();
            content.put("comment", request.getMentorFeedback());
            content.put("scores", Map.of("TOTAL", request.getMentorScore()));
            if (request.getProofUrl() != null && !request.getProofUrl().isBlank()) {
                content.put("proofUrl", request.getProofUrl());
            }
            f.setContent(content);
            f.setStatus("CONFIRMED");
            portfolioFormRepository.save(f);
        });

        auditLogService.logAction(
            actor.getId(),
            "DIGITIZE_SEALED_PAPER_EVALUATION",
            "InternshipPlacement",
            placement.getId(),
            "SUCCESS",
            Map.of("mentorScore", request.getMentorScore(), "proofUrl", Objects.toString(request.getProofUrl(), "")),
            null
        );

        log.info("GVHD {} đã số hóa phiếu niêm phong của cơ quan cho SV {}: điểm={}",
            actor.getEmail(), placement.getStudent().getFullName(), request.getMentorScore());

        return RubricEvaluationResponse.builder()
            .id(savedRubric.getId())
            .placementId(placement.getId())
            .evaluatorId(mentorUser.getId())
            .evaluatorName(mentorUser.getFullName())
            .evaluatorRole(UserRole.COMPANY_MENTOR)
            .evaluationStage(RubricStage.FINAL)
            .rubricVersion(savedRubric.getRubricVersion())
            .finalScore(savedRubric.getFinalScore())
            .qualitativeFeedback(savedRubric.getQualitativeFeedback())
            .status(savedRubric.getStatus())
            .submittedAt(savedRubric.getSubmittedAt())
            .build();
    }
}
