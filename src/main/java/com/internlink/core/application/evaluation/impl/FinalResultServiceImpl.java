package com.internlink.core.application.evaluation.impl;

import com.internlink.core.application.organization.TermDepartmentScope;
import com.internlink.core.application.evaluation.AssessmentRules;
import com.internlink.core.application.evaluation.CtuGradingHelper;
import com.internlink.core.application.evaluation.FinalResultService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.evaluation.AssessmentComponentScore;
import com.internlink.core.domain.evaluation.FinalResult;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.PortfolioForm;
import com.internlink.core.domain.evaluation.RubricEvaluation;
import com.internlink.core.presentation.evaluation.dto.request.BatchPublishResultRequest;
import com.internlink.core.presentation.evaluation.dto.request.FinalResultRequest;
import com.internlink.core.presentation.evaluation.dto.response.BatchPublishResultResponse;
import com.internlink.core.presentation.evaluation.dto.response.FacultyEvaluationSummaryResponse;
import com.internlink.core.presentation.evaluation.dto.response.FinalResultResponse;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.ResultStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinalResultServiceImpl implements FinalResultService {

    private final JpaFinalResultRepository finalResultRepository;
    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaUserRepository userRepository;
    private final JpaInternshipTermRepository termRepository;
    private final SecurityGuard securityGuard;

    private final FinalResultMapper finalResultMapper;
    private final FinalizationReadinessValidator readinessValidator;
    private final FinalResultNotificationHelper notificationHelper;
    private final TermEvaluationSummaryBuilder summaryBuilder;
    private final FinalResultBatchPublisher batchPublisher;
    private final TermDepartmentScope termScope;

    @Override
    @Transactional(readOnly = true)
    public FinalResultResponse getFinalResultByPlacement(UUID placementId, UUID requestingUserId) {
        FinalResult result = finalResultRepository.findByPlacementId(placementId)
            .orElseThrow(() -> new ResourceNotFoundException("FinalResult", "placementId", placementId));

        User requestingUser = userRepository.findById(requestingUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", requestingUserId));

        ResourceAuthorization.require(ResourceAuthorization.canReadPlacement(
            requestingUser, result.getPlacement()));

        if (requestingUser.getRole() == UserRole.STUDENT) {
            securityGuard.requireSelf(requestingUserId, result.getPlacement().getStudent().getId(), "FinalResult");
            if (result.getPublishedAt() == null) {
                throw new BadRequestException("Kết quả thực tập của bạn đang được Hội đồng xét duyệt, chưa được công bố chính thức.");
            }
        }

        return finalResultMapper.toResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public FinalResultResponse getFinalResultByPlacement(UUID placementId) {
        return getFinalResultByPlacement(placementId, securityGuard.currentUser().getId());
    }

    @Override
    @Transactional
    public FinalResultResponse calculateAndFinalizeResult(UUID decidedByUserId, FinalResultRequest request) {
        InternshipPlacement placement = placementRepository.findById(request.getPlacementId())
            .orElseThrow(() -> new ResourceNotFoundException("InternshipPlacement", "id", request.getPlacementId()));
        TermGuard.requireNotClosed(placement.getTerm());

        User admin = userRepository.findById(decidedByUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", decidedByUserId));
        ResourceAuthorization.require(ResourceAuthorization.canGrade(admin, placement));

        if (request.getMentorScore() != null || request.getLecturerScore() != null
            || request.getComplianceScore() != null || request.getMentorWeight() != null
            || request.getLecturerWeight() != null || request.getComplianceWeight() != null
            || Boolean.FALSE.equals(request.getAutoAggregateFromRubrics())
            || request.getOverrideReason() != null
            || request.getComponentBreakdown() != null && !request.getComponentBreakdown().isEmpty()) {
            throw new BadRequestException("Không được nhập điểm hoặc trọng số thủ công; hệ thống lấy theo đề cương đã duyệt");
        }

        FinalResult existing = finalResultRepository.findByPlacementId(placement.getId()).orElse(null);
        if (existing != null && existing.getPublishedAt() != null) {
            throw new BadRequestException("Kết quả đã công bố, không thể tính lại trực tiếp");
        }

        Map<String, AssessmentComponentScore> verifiedScores = readinessValidator.requireReadyForFinalization(placement);
        Map<String, Object> componentBreakdown = new LinkedHashMap<>();
        BigDecimal total10 = BigDecimal.ZERO;
        for (Map<String, Object> definition : placement.getAssessmentScheme().getComponents()) {
            String code = String.valueOf(definition.get("code"));
            AssessmentComponentScore component = verifiedScores.get(code);
            BigDecimal weight = AssessmentRules.number(definition.get("weight"));
            total10 = total10.add(component.getScore().multiply(weight));
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("score", component.getScore());
            detail.put("weight", weight);
            detail.put("source", component.getSource());
            detail.put("evidenceDocumentId", component.getEvidenceDocument() != null
                ? component.getEvidenceDocument().getId().toString() : null);
            detail.put("criteriaScores", component.getCriteriaScores());
            componentBreakdown.put(code, detail);
        }
        total10 = total10.setScale(1, RoundingMode.HALF_UP);
        CtuGradingHelper.CtuGrade ctuGrade = CtuGradingHelper.convertFromScale10(total10);

        ResultStatus resolvedStatus = ctuGrade.getResultStatus();
        if (request.getResultStatus() != null && request.getResultStatus() != resolvedStatus) {
            throw new BadRequestException("Trạng thái đạt/không đạt không khớp điểm tổng kết");
        }

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("assessment_scheme_id", placement.getAssessmentScheme().getId().toString());
        breakdown.put("course_code", placement.getAssessmentScheme().getCourseCode());
        breakdown.put("revision", placement.getAssessmentScheme().getRevision());
        breakdown.put("components", componentBreakdown);
        breakdown.put("ctu_scale_4", ctuGrade.getScoreScale4());
        breakdown.put("ctu_letter_grade", ctuGrade.getLetterGrade());
        breakdown.put("ctu_classification", ctuGrade.getClassification());

        FinalResult result = existing != null ? existing : FinalResult.builder().placement(placement).build();
        result.setMentorScore(null);
        result.setLecturerScore(null);
        result.setComplianceScore(null);
        result.setComponentBreakdown(breakdown);
        result.setFinalScore(total10);
        result.setResultStatus(resolvedStatus);
        result.setDecidedBy(admin);
        result.setFinalizedAt(OffsetDateTime.now());

        boolean publish = Boolean.TRUE.equals(request.getPublishImmediately());
        if (publish) {
            result.setPublishedAt(OffsetDateTime.now());
            if (resolvedStatus == ResultStatus.PASSED) {
                placement.setStatus(PlacementStatus.COMPLETED);
                placementRepository.save(placement);
            }
        } else {
            result.setPublishedAt(null);
        }

        FinalResult saved = finalResultRepository.save(result);

        if (publish) {
            notificationHelper.logAndNotifyFinalizePublished(decidedByUserId, saved, total10, resolvedStatus, ctuGrade);
        } else {
            notificationHelper.logDraft(decidedByUserId, saved, total10);
        }

        log.info("Tổng hợp điểm thực tập cho placement {}: thang 10={}, hệ 4={}, điểm chữ={}, published={}",
            placement.getId(), total10, ctuGrade.getScoreScale4(), ctuGrade.getLetterGrade(), publish);

        return finalResultMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public FinalResultResponse publishFinalResult(UUID placementId, UUID publishedByUserId) {
        FinalResult result = finalResultRepository.findByPlacementId(placementId)
            .orElseThrow(() -> new ResourceNotFoundException("FinalResult", "placementId", placementId));
        TermGuard.requireNotClosed(result.getPlacement().getTerm());

        if (result.getFinalizedAt() == null) {
            throw new BadRequestException("Kết quả thực tập chưa được tổng hợp điểm. Vui lòng tổng hợp điểm trước khi công bố.");
        }
        if (result.getPublishedAt() != null) throw new BadRequestException("Kết quả đã được công bố");
        readinessValidator.requireReadyForFinalization(result.getPlacement());
        if (result.getPlacement().getAssessmentScheme() == null
            || !result.getPlacement().getAssessmentScheme().getId().toString().equals(
                result.getComponentBreakdown().get("assessment_scheme_id"))) {
            throw new BadRequestException("Bản nháp không dùng phương án đánh giá hiện hành; cần tổng hợp lại");
        }

        User publisher = userRepository.findById(publishedByUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", publishedByUserId));
        ResourceAuthorization.require(ResourceAuthorization.canGrade(publisher, result.getPlacement()));

        result.setPublishedAt(OffsetDateTime.now());
        result.setDecidedBy(publisher);

        if (result.getResultStatus() == ResultStatus.PASSED) {
            InternshipPlacement placement = result.getPlacement();
            placement.setStatus(PlacementStatus.COMPLETED);
            placementRepository.save(placement);
        }

        FinalResult saved = finalResultRepository.save(result);
        notificationHelper.logAndNotifyPublished(publishedByUserId, saved, placementId);

        log.info("Đã công bố chính thức kết quả thực tập cho placement {}", placementId);
        return finalResultMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FacultyEvaluationSummaryResponse> getTermEvaluationSummary(UUID termId, UUID facultyAdminId) {
        InternshipTerm term = termRepository.findById(termId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));

        User admin = userRepository.findById(facultyAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", facultyAdminId));

        return summaryBuilder.buildSummary(termId, termScope.gradablePlacements(termId, admin));
    }

    @Override
    @Transactional
    public com.internlink.core.presentation.evaluation.dto.response.BatchFinalizeResponse batchFinalizeTerm(UUID termId, UUID facultyAdminId) {
        InternshipTerm term = termRepository.findById(termId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        TermGuard.requireNotClosed(term);

        User admin = userRepository.findById(facultyAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", facultyAdminId));

        List<InternshipPlacement> placements = termScope.gradablePlacements(termId, admin);
        int finalizedCount = 0;
        int skippedCount = 0;
        List<String> messages = new ArrayList<>();

        for (InternshipPlacement p : placements) {
            try {
                quickFinalizePlacement(p.getId(), facultyAdminId);
                finalizedCount++;
            } catch (Exception ex) {
                skippedCount++;
                String studentName = p.getStudent() != null ? p.getStudent().getFullName() : p.getId().toString();
                messages.add("SV " + studentName + ": Chưa thể tổng hợp (" + ex.getMessage() + ")");
            }
        }

        return com.internlink.core.presentation.evaluation.dto.response.BatchFinalizeResponse.builder()
            .finalizedCount(finalizedCount)
            .skippedCount(skippedCount)
            .messages(messages)
            .build();
    }

    @Override
    @Transactional
    public BatchPublishResultResponse publishBatchResults(UUID termId, BatchPublishResultRequest request, UUID facultyAdminId) {
        return batchPublisher.publishBatchResults(termId, request, facultyAdminId, this);
    }

    @Override
    @Transactional
    public FinalResultResponse quickFinalizePlacement(UUID placementId, UUID facultyAdminId) {
        InternshipPlacement placement = placementRepository.findById(placementId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipPlacement", "id", placementId));
        TermGuard.requireNotClosed(placement.getTerm());

        User admin = userRepository.findById(facultyAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", facultyAdminId));

        ResourceAuthorization.require(ResourceAuthorization.canGrade(admin, placement));

        FinalResult existing = finalResultRepository.findByPlacementId(placementId).orElse(null);
        if (existing != null && existing.getPublishedAt() != null) {
            throw new BadRequestException("Kết quả đã công bố chính thức, không thể thay đổi");
        }

        List<FacultyEvaluationSummaryResponse> list = getTermEvaluationSummary(placement.getTerm().getId(), facultyAdminId);
        FacultyEvaluationSummaryResponse summary = list.stream()
            .filter(s -> s.getPlacementId().equals(placementId))
            .findFirst()
            .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin đánh giá của sinh viên"));

        if (summary.getFinalScore() == null) {
            throw new BadRequestException("Chưa có điểm đánh giá từ Giảng viên hoặc Doanh nghiệp để tổng hợp");
        }

        CtuGradingHelper.CtuGrade ctuGrade = CtuGradingHelper.convertFromScale10(summary.getFinalScore());

        FinalResult result = existing != null ? existing : FinalResult.builder().placement(placement).build();
        result.setMentorScore(summary.getMentorScore());
        result.setLecturerScore(summary.getLecturerScore());
        result.setComplianceScore(summary.getComplianceScore());
        result.setFinalScore(summary.getFinalScore());
        result.setResultStatus(ctuGrade.getResultStatus());
        result.setDecidedBy(admin);
        result.setFinalizedAt(OffsetDateTime.now());

        Map<String, Object> breakdown = new LinkedHashMap<>();
        if (placement.getAssessmentScheme() != null) {
            breakdown.put("assessment_scheme_id", placement.getAssessmentScheme().getId().toString());
            breakdown.put("course_code", placement.getAssessmentScheme().getCourseCode());
        }
        breakdown.put("ctu_scale_4", ctuGrade.getScoreScale4());
        breakdown.put("ctu_letter_grade", ctuGrade.getLetterGrade());
        breakdown.put("ctu_classification", ctuGrade.getClassification());
        result.setComponentBreakdown(breakdown);

        FinalResult saved = finalResultRepository.save(result);
        return finalResultMapper.toResponse(saved);
    }
}
