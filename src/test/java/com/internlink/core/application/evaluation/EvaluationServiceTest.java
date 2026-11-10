package com.internlink.core.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.internlink.core.application.evaluation.impl.*;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.evaluation.AssessmentComponentScore;
import com.internlink.core.domain.evaluation.AssessmentScheme;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.InternshipReport;
import com.internlink.core.domain.placement.LearningAgreement;
import com.internlink.core.domain.placement.WeeklyLogbook;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.evaluation.dto.request.FinalResultRequest;
import com.internlink.core.presentation.evaluation.dto.request.RubricEvaluationRequest;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock private JpaRubricEvaluationRepository rubricRepository;
    @Mock private JpaInternshipPlacementRepository placementRepository;
    @Mock private JpaUserRepository userRepository;
    @Mock private SecurityGuard securityGuard;
    @Mock private JpaAssessmentComponentScoreRepository componentScoreRepository;
    @Mock private JpaWeeklyLogbookRepository logbookRepository;
    @Mock private JpaInternshipReportRepository reportRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;
    @Mock private JpaFinalResultRepository finalResultRepository;
    @Mock private JpaStudentRosterRepository studentRosterRepository;
    @Mock private JpaStudentProfileRepository studentProfileRepository;
    @Mock private JpaPortfolioFormRepository portfolioFormRepository;
    @Mock private JpaInternshipTermRepository termRepository;

    private RubricEvaluationServiceImpl rubricService;
    private FinalResultServiceImpl finalResultService;

    @BeforeEach
    void setUp() {
        rubricService = new RubricEvaluationServiceImpl(
                rubricRepository, placementRepository, userRepository, securityGuard);

        FinalResultMapper finalResultMapper = new FinalResultMapper(studentProfileRepository);
        FinalizationReadinessValidator readinessValidator = new FinalizationReadinessValidator(
                componentScoreRepository, logbookRepository, reportRepository);
        FinalResultNotificationHelper finalResultNotificationHelper =
                new FinalResultNotificationHelper(auditLogService, notificationService);
        TermEvaluationSummaryBuilder summaryBuilder = new TermEvaluationSummaryBuilder(
                placementRepository, studentRosterRepository, studentProfileRepository,
                finalResultRepository, portfolioFormRepository, componentScoreRepository, rubricRepository);
        var termScope = new com.internlink.core.application.organization.TermDepartmentScope(
                studentRosterRepository, placementRepository, org.mockito.Mockito.mock(com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository.class));
        FinalResultBatchPublisher batchPublisher = new FinalResultBatchPublisher(
                termRepository, userRepository, placementRepository, finalResultRepository, termScope);

        finalResultService = new FinalResultServiceImpl(
                finalResultRepository, placementRepository, userRepository, termRepository,
                securityGuard, finalResultMapper, readinessValidator, finalResultNotificationHelper,
                summaryBuilder, batchPublisher, termScope);

        lenient().when(rubricRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(finalResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(placementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rubricRejectsRoleNotSupportedByDatabaseConstraint() {
        InternshipPlacement placement = placement();
        User admin = user(UserRole.ADMIN);
        when(placementRepository.findById(placement.getId())).thenReturn(Optional.of(placement));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(admin));

        assertThatThrownBy(() -> rubricService.submitEvaluation(admin.getId(), rubricRequest(placement.getId())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rubricDraftDoesNotSetSubmittedTimestamp() {
        InternshipPlacement placement = placement();
        User mentor = placement.getMentor();
        RubricEvaluationRequest request = rubricRequest(placement.getId());
        request.setStatus("draft");
        when(placementRepository.findById(placement.getId())).thenReturn(Optional.of(placement));
        when(userRepository.findById(mentor.getId())).thenReturn(Optional.of(mentor));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(mentor));
        when(rubricRepository.findByPlacementIdAndEvaluatorIdAndEvaluationStage(
                placement.getId(), mentor.getId(), request.getEvaluationStage()))
                .thenReturn(Optional.empty());

        assertThat(rubricService.submitEvaluation(mentor.getId(), request).getSubmittedAt()).isNull();
    }

    @Test
    void finalResultUsesApprovedCourseSchemeAndCompletesPlacement() {
        InternshipPlacement placement = placement();
        AssessmentScheme scheme = AssessmentScheme.builder()
                .term(placement.getTerm())
                .components(List.of(
                        Map.of("code", "HOST", "name", "Cơ sở", "weight", "0.60", "assessorRole", "COMPANY_MENTOR"),
                        Map.of("code", "REPORT", "name", "Báo cáo", "weight", "0.40", "assessorRole", "LECTURER")))
                .requiredLogbookWeeks(1)
                .requireMidtermReport(false)
                .requireFinalReport(true)
                .courseCode("CT454")
                .revision(1)
                .status("APPROVED")
                .build();
        scheme.setId(UUID.randomUUID());
        placement.setAssessmentScheme(scheme);

        WeeklyLogbook logbook = WeeklyLogbook.builder().placement(placement).weekNumber(1).status(LogbookStatus.APPROVED_BY_MENTOR).build();
        Document reportFile = Document.builder().status("ACTIVE").build();
        InternshipReport report = InternshipReport.builder().placement(placement).reportType("FINAL").document(reportFile).status("APPROVED").build();

        AssessmentComponentScore hostScore = AssessmentComponentScore.builder()
                .placement(placement).componentCode("HOST").score(new BigDecimal("8.0"))
                .status("VERIFIED").source("ONLINE").criteriaScores(Map.of()).build();
        AssessmentComponentScore lecturerScore = AssessmentComponentScore.builder()
                .placement(placement).componentCode("REPORT").score(new BigDecimal("7.0"))
                .status("VERIFIED").source("ONLINE").criteriaScores(Map.of()).build();

        User admin = user(UserRole.ADMIN);
        when(placementRepository.findById(placement.getId())).thenReturn(Optional.of(placement));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(finalResultRepository.findByPlacementId(placement.getId())).thenReturn(Optional.empty());
        when(studentProfileRepository.findById(placement.getStudent().getId())).thenReturn(Optional.empty());
        when(logbookRepository.findByPlacementIdOrderByWeekNumberAsc(placement.getId())).thenReturn(List.of(logbook));
        when(reportRepository.findByPlacementId(placement.getId())).thenReturn(List.of(report));
        when(componentScoreRepository.findByPlacementId(placement.getId())).thenReturn(List.of(hostScore, lecturerScore));

        FinalResultRequest request = FinalResultRequest.builder().placementId(placement.getId()).publishImmediately(true).build();
        var response = finalResultService.calculateAndFinalizeResult(admin.getId(), request);

        assertThat(response.getFinalScore()).isEqualByComparingTo("7.6");
        assertThat(response.getScoreScale4()).isEqualByComparingTo("3.0");
        assertThat(response.getLetterGrade()).isEqualTo("B");
        assertThat(placement.getStatus()).isEqualTo(PlacementStatus.COMPLETED);
    }

    @Test
    void noSchemeCannotBeUsedAsFinalScore() {
        InternshipPlacement placement = placement();
        User admin = user(UserRole.ADMIN);
        when(placementRepository.findById(placement.getId())).thenReturn(Optional.of(placement));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(finalResultRepository.findByPlacementId(placement.getId())).thenReturn(Optional.empty());

        FinalResultRequest request = FinalResultRequest.builder().placementId(placement.getId()).build();

        assertThatThrownBy(() -> finalResultService.calculateAndFinalizeResult(admin.getId(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("phương án đánh giá");
        verify(finalResultRepository, never()).save(any());
    }

    private RubricEvaluationRequest rubricRequest(UUID placementId) {
        return RubricEvaluationRequest.builder()
                .placementId(placementId)
                .evaluationStage(RubricStage.FINAL)
                .rubricVersion("v1")
                .criteriaScores(List.of(Map.of("score", 8)))
                .finalScore(new BigDecimal("8.0"))
                .status("SUBMITTED")
                .build();
    }

    private InternshipPlacement placement() {
        User student = user(UserRole.STUDENT);
        User mentor = user(UserRole.COMPANY_MENTOR);
        User lecturer = user(UserRole.LECTURER);
        Company company = Company.builder().companyName("Acme").taxCode("123").address(Map.of()).build();
        company.setId(UUID.randomUUID());
        Department department = Department.builder().code(UUID.randomUUID().toString()).name("CNTT").contactEmail("it@example.com").build();
        department.setId(UUID.randomUUID());
        InternshipTerm term = InternshipTerm.builder().code("T1").termName("Term").build();
        term.setId(UUID.randomUUID());
        LearningAgreement agreement = LearningAgreement.builder().student(student).company(company).department(department).targetCredits(10).learningObjectives("Objectives").build();
        agreement.setId(UUID.randomUUID());
        InternshipPlacement value = InternshipPlacement.builder()
                .agreement(agreement).student(student).company(company).mentor(mentor).lecturer(lecturer).term(term)
                .totalHoursWorked(BigDecimal.ZERO).status(PlacementStatus.ACTIVE).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private User user(UserRole role) {
        User value = User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash").fullName("User").role(role).build();
        value.setId(UUID.randomUUID());
        return value;
    }
}
