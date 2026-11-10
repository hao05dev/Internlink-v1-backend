package com.internlink.core.application.evaluation.impl;

import com.internlink.core.application.evaluation.CtuGradingHelper;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.evaluation.AssessmentComponentScore;
import com.internlink.core.domain.evaluation.FinalResult;
import com.internlink.core.domain.evaluation.RubricEvaluation;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.PortfolioForm;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.evaluation.dto.response.FacultyEvaluationSummaryResponse;
import com.internlink.core.shared.enums.ResultStatus;
import com.internlink.core.shared.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class TermEvaluationSummaryBuilder {

    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaStudentRosterRepository studentRosterRepository;
    private final JpaStudentProfileRepository studentProfileRepository;
    private final JpaFinalResultRepository finalResultRepository;
    private final JpaPortfolioFormRepository portfolioFormRepository;
    private final JpaAssessmentComponentScoreRepository componentScoreRepository;
    private final JpaRubricEvaluationRepository rubricRepository;

    public List<FacultyEvaluationSummaryResponse> buildSummary(UUID termId, List<InternshipPlacement> placements) {
        List<FacultyEvaluationSummaryResponse> summaryList = new ArrayList<>();

        for (InternshipPlacement p : placements) {
            User student = p.getStudent();
            String studentCode = null;
            String studentName = student != null ? student.getFullName() : "";
            String studentEmail = student != null ? student.getEmail() : "";
            String classCode = null;
            UUID programId = null;
            String programName = null;
            String academicYear = null;

            Optional<StudentRoster> rosterOpt = studentRosterRepository.findByTermIdAndClaimedUserId(termId, student.getId());
            if (rosterOpt.isPresent()) {
                StudentRoster r = rosterOpt.get();
                studentCode = r.getStudentCode();
                if (r.getFullName() != null) studentName = r.getFullName();
                if (r.getOfficialEmail() != null) studentEmail = r.getOfficialEmail();
                classCode = r.getClassCode();
                academicYear = r.getAcademicYear();
                if (r.getProgram() != null) {
                    programId = r.getProgram().getId();
                    programName = r.getProgram().getName();
                }
            } else if (student != null) {
                studentProfileRepository.findById(student.getId()).ifPresent(prof -> {
                    if (prof.getProgram() != null) {
                        // fallback
                    }
                });
            }

            String companyName = p.getCompany() != null
                ? p.getCompany().getCompanyName()
                : (p.getStudentFoundApplication() != null ? p.getStudentFoundApplication().getHostName() : "Doanh nghiệp tự liên hệ");

            UUID mentorId = p.getMentor() != null ? p.getMentor().getId() : null;
            String mentorName = p.getMentor() != null ? p.getMentor().getFullName() : null;
            String mentorEmail = p.getMentor() != null ? p.getMentor().getEmail() : null;

            UUID lecturerId = p.getLecturer() != null ? p.getLecturer().getId() : null;
            String lecturerName = p.getLecturer() != null ? p.getLecturer().getFullName() : null;
            String lecturerEmail = p.getLecturer() != null ? p.getLecturer().getEmail() : null;

            Optional<FinalResult> finalResultOpt = finalResultRepository.findByPlacementId(p.getId());
            Optional<PortfolioForm> m03Opt = portfolioFormRepository.findByPlacementIdAndKind(p.getId(), "M03");
            Optional<PortfolioForm> m04Opt = portfolioFormRepository.findByPlacementIdAndKind(p.getId(), "M04");
            List<AssessmentComponentScore> componentScores = componentScoreRepository.findByPlacementId(p.getId());
            List<RubricEvaluation> rubrics = rubricRepository.findByPlacementId(p.getId());

            BigDecimal mentorScore = null;
            String mentorFeedback = null;
            String formM04Status = m03Opt.map(PortfolioForm::getStatus).orElse("NOT_STARTED");

            if (m03Opt.isPresent()) {
                Map<String, Object> content = m03Opt.get().getContent();
                if (content != null) {
                    if (content.get("comment") instanceof String c) mentorFeedback = c;
                    if (content.get("scores") instanceof Map<?, ?> scMap && !scMap.isEmpty()) {
                        double sum = 0;
                        int count = 0;
                        for (Object val : scMap.values()) {
                            if (val instanceof Number num) {
                                sum += num.doubleValue();
                                count++;
                            }
                        }
                        if (count > 0) mentorScore = BigDecimal.valueOf(sum / count).setScale(1, RoundingMode.HALF_UP);
                    }
                }
            }

            if (mentorScore == null) {
                for (AssessmentComponentScore cs : componentScores) {
                    if ("COMPANY_MENTOR".equals(cs.getSource()) || "OFFLINE".equalsIgnoreCase(cs.getSource()) || "M03".equalsIgnoreCase(cs.getComponentCode())) {
                        mentorScore = cs.getScore();
                        break;
                    }
                }
            }
            if (mentorScore == null) {
                for (RubricEvaluation r : rubrics) {
                    if (r.getEvaluatorRole() == UserRole.COMPANY_MENTOR && r.getFinalScore() != null) {
                        mentorScore = r.getFinalScore();
                        if (mentorFeedback == null) mentorFeedback = r.getQualitativeFeedback();
                        break;
                    }
                }
            }

            BigDecimal lecturerScore = null;
            String lecturerFeedback = null;
            String formM05Status = m04Opt.map(PortfolioForm::getStatus).orElse("NOT_STARTED");

            if (m04Opt.isPresent()) {
                Map<String, Object> content = m04Opt.get().getContent();
                if (content != null) {
                    if (content.get("comment") instanceof String c) lecturerFeedback = c;
                    if (content.get("academicTotals") instanceof Map<?, ?> totalsMap && totalsMap.get("TOTAL") instanceof Number n) {
                        lecturerScore = BigDecimal.valueOf(n.doubleValue()).setScale(1, RoundingMode.HALF_UP);
                    } else if (content.get("academicScores") instanceof Map<?, ?> acMap && !acMap.isEmpty()) {
                        double sum = 0;
                        int count = 0;
                        for (Object val : acMap.values()) {
                            if (val instanceof Map<?, ?> item && item.get("score") instanceof Number num) {
                                sum += num.doubleValue();
                                count++;
                            }
                        }
                        if (count > 0) lecturerScore = BigDecimal.valueOf(sum / count).setScale(1, RoundingMode.HALF_UP);
                    }
                }
            }

            if (lecturerScore == null) {
                for (AssessmentComponentScore cs : componentScores) {
                    if ("LECTURER".equals(cs.getSource()) || "M04".equalsIgnoreCase(cs.getComponentCode())) {
                        lecturerScore = cs.getScore();
                        break;
                    }
                }
            }
            if (lecturerScore == null) {
                for (RubricEvaluation r : rubrics) {
                    if (r.getEvaluatorRole() == UserRole.LECTURER && r.getFinalScore() != null) {
                        lecturerScore = r.getFinalScore();
                        if (lecturerFeedback == null) lecturerFeedback = r.getQualitativeFeedback();
                        break;
                    }
                }
            }

            BigDecimal complianceScore = null;
            BigDecimal finalScore = null;
            BigDecimal scoreScale4 = null;
            String letterGrade = null;
            String classification = null;
            ResultStatus resultStatus = null;
            boolean isFinalized = false;
            boolean isPublished = false;
            OffsetDateTime publishedAt = null;
            OffsetDateTime finalizedAt = null;

            if (finalResultOpt.isPresent()) {
                FinalResult fr = finalResultOpt.get();
                finalScore = fr.getFinalScore();
                if (fr.getMentorScore() != null) mentorScore = fr.getMentorScore();
                if (fr.getLecturerScore() != null) lecturerScore = fr.getLecturerScore();
                complianceScore = fr.getComplianceScore();
                resultStatus = fr.getResultStatus();
                isFinalized = (fr.getFinalizedAt() != null);
                isPublished = (fr.getPublishedAt() != null);
                publishedAt = fr.getPublishedAt();
                finalizedAt = fr.getFinalizedAt();

                if (finalScore != null) {
                    CtuGradingHelper.CtuGrade ctuGrade = CtuGradingHelper.convertFromScale10(finalScore);
                    scoreScale4 = ctuGrade.getScoreScale4();
                    letterGrade = ctuGrade.getLetterGrade();
                    classification = ctuGrade.getClassification();
                    if (resultStatus == null) {
                        resultStatus = ctuGrade.getResultStatus();
                    }
                }
            } else {
                resultStatus = (mentorScore != null || lecturerScore != null)
                    ? ResultStatus.PENDING_REVIEW
                    : ResultStatus.NOT_COMPLETED;
            }

            summaryList.add(FacultyEvaluationSummaryResponse.builder()
                .placementId(p.getId())
                .studentId(student != null ? student.getId() : null)
                .studentCode(studentCode)
                .studentName(studentName)
                .studentEmail(studentEmail)
                .classCode(classCode)
                .programId(programId)
                .programName(programName)
                .academicYear(academicYear)
                .companyId(p.getCompany() != null ? p.getCompany().getId() : null)
                .companyName(companyName)
                .mentorId(mentorId)
                .mentorName(mentorName)
                .mentorEmail(mentorEmail)
                .mentorScore(mentorScore)
                .mentorFeedback(mentorFeedback)
                .lecturerId(lecturerId)
                .lecturerName(lecturerName)
                .lecturerEmail(lecturerEmail)
                .lecturerScore(lecturerScore)
                .lecturerFeedback(lecturerFeedback)
                .complianceScore(complianceScore)
                .finalScore(finalScore)
                .scoreScale4(scoreScale4)
                .letterGrade(letterGrade)
                .classification(classification)
                .resultStatus(resultStatus)
                .placementStatus(p.getStatus())
                .isFinalized(isFinalized)
                .isPublished(isPublished)
                .publishedAt(publishedAt)
                .finalizedAt(finalizedAt)
                .formM04Status(formM04Status)
                .formM05Status(formM05Status)
                .build());
        }

        return summaryList;
    }
}
