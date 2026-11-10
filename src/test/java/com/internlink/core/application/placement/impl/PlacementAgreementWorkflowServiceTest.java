package com.internlink.core.application.placement.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.internlink.core.application.placement.PlacementEligibilityService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.placement.*;
import com.internlink.core.domain.recruitment.JobApplication;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.recruitment.PlacementOffer;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.placement.dto.request.AttendanceLogRequest;
import com.internlink.core.presentation.placement.dto.request.WeeklyLogbookRequest;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PlacementAgreementWorkflowServiceTest {

    @Mock JpaAttendanceLogRepository attendanceRepository;
    @Mock JpaInternshipPlacementRepository placementRepository;
    @Mock JpaUserRepository userRepository;
    @Mock JpaPlacementTaskRepository taskRepository;
    @Mock JpaWeeklyLogbookRepository logbookRepository;
    @Mock JpaLearningAgreementRepository agreementRepository;
    @Mock JpaPlacementOfferRepository offerRepository;
    @Mock JpaDepartmentRepository departmentRepository;
    @Mock JpaStudentProfileRepository studentProfileRepository;
    @Mock SecurityGuard securityGuard;
    @Mock AuditLogService auditLogService;
    @Mock NotificationService notificationService;

    private AttendanceLogServiceImpl attendanceService;
    private PlacementTaskServiceImpl taskService;
    private WeeklyLogbookServiceImpl logbookService;
    private LearningAgreementServiceImpl agreementService;

    @BeforeEach
    void setUp() {
        attendanceService =
                new AttendanceLogServiceImpl(
                        attendanceRepository, placementRepository, userRepository);
        taskService =
                new PlacementTaskServiceImpl(
                        taskRepository,
                        placementRepository,
                        userRepository,
                        mock(
                                com.internlink.core.application.system.notification
                                        .PlacementWorkflowNotificationHelper.class));
        logbookService =
                new WeeklyLogbookServiceImpl(
                        logbookRepository,
                        placementRepository,
                        userRepository,
                        securityGuard,
                        notificationService);
        PlacementEligibilityService placementEligibility =
                new PlacementEligibilityService(placementRepository, null, null);
        agreementService =
                new LearningAgreementServiceImpl(
                        agreementRepository,
                        offerRepository,
                        userRepository,
                        studentProfileRepository,
                        placementEligibility,
                        securityGuard,
                        auditLogService,
                        notificationService);
        lenient()
                .when(attendanceRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(placementRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(taskRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(logbookRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(agreementRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void logbookReviewRejectsUnrelatedMentor() {
        InternshipPlacement placement = placement();
        WeeklyLogbook logbook =
                WeeklyLogbook.builder()
                        .placement(placement)
                        .weekNumber(1)
                        .periodStart(LocalDate.now())
                        .periodEnd(LocalDate.now())
                        .tasksCompleted("Tasks")
                        .learningReflection("Learned")
                        .totalHours(BigDecimal.TEN)
                        .status(LogbookStatus.SUBMITTED)
                        .build();
        logbook.setId(UUID.randomUUID());
        User outsider = user(UserRole.COMPANY_MENTOR);
        when(logbookRepository.findById(logbook.getId())).thenReturn(Optional.of(logbook));
        when(userRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        assertThatThrownBy(
                        () ->
                                logbookService.reviewByMentor(
                                         logbook.getId(),
                                        outsider.getId(),
                                        LogbookStatus.APPROVED_BY_MENTOR,
                                        null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void unrelatedStudentCannotReadAnotherPlacementLogbook() {
        InternshipPlacement placement = placement();
        User outsider = user(UserRole.STUDENT);
        when(placementRepository.findById(placement.getId())).thenReturn(Optional.of(placement));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(outsider));
        when(userRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> logbookService.getLogbooksByPlacement(placement.getId()))
                .isInstanceOf(ForbiddenException.class);
        verify(logbookRepository, never()).findByPlacementIdOrderByWeekNumberAsc(any());
    }

    @Test
    void agreementCanOnlyBeCreatedByOfferOwner() {
        PlacementOffer offer = offer();
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        var request =
                com.internlink.core.presentation.placement.dto.request.LearningAgreementRequest
                        .builder()
                        .offerId(offer.getId())
                        .targetCredits(10)
                        .learningObjectives("Objectives")
                        .build();

        assertThatThrownBy(() -> agreementService.createAgreement(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void thirdSignatureApprovesAgreement() {
        PlacementOffer offer = offer();
        Department department = offer.getApplication().getStudent().getDepartment();
        LearningAgreement agreement =
                LearningAgreement.builder()
                        .offer(offer)
                        .student(offer.getApplication().getStudent())
                        .company(offer.getApplication().getJob().getCompany())
                        .department(department)
                        .targetCredits(10)
                        .learningObjectives("Objectives")
                        .status(AgreementStatus.PENDING_SIGNATURES)
                        .studentSignature(Map.of("signed", true))
                        .companySignature(Map.of("signed", true))
                        .build();
        agreement.setId(UUID.randomUUID());
        when(agreementRepository.findById(agreement.getId())).thenReturn(Optional.of(agreement));
        User admin = user(UserRole.ADMIN);
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThat(
                        agreementService
                                .signAgreement(agreement.getId(), "ADMIN", Map.of("signed", true))
                                .getStatus())
                .isEqualTo(AgreementStatus.APPROVED);
    }

    private AttendanceLogRequest attendanceRequest(UUID placementId) {
        return AttendanceLogRequest.builder()
                .placementId(placementId)
                .workDate(LocalDate.now())
                .checkInAt(OffsetDateTime.now())
                .workFormat(WorkFormat.ONSITE)
                .build();
    }

    private InternshipPlacement placement() {
        User student = user(UserRole.STUDENT);
        User mentor = user(UserRole.COMPANY_MENTOR);
        User lecturer = user(UserRole.LECTURER);
        Company company = company();
        Department department = department();
        PlacementOffer offer = offer(student, mentor, company, department);
        LearningAgreement agreement =
                LearningAgreement.builder()
                        .offer(offer)
                        .student(student)
                        .company(company)
                        .department(department)
                        .targetCredits(10)
                        .learningObjectives("Objectives")
                        .status(AgreementStatus.APPROVED)
                        .build();
        agreement.setId(UUID.randomUUID());
        InternshipPlacement value =
                InternshipPlacement.builder()
                        .agreement(agreement)
                        .student(student)
                        .company(company)
                        .mentor(mentor)
                        .lecturer(lecturer)
                        .term(offer.getApplication().getJob().getTerm())
                        .startDate(LocalDate.now())
                        .endDate(LocalDate.now().plusMonths(3))
                        .totalHoursWorked(BigDecimal.ZERO)
                        .status(PlacementStatus.ACTIVE)
                        .build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private PlacementOffer offer() {
        User student = user(UserRole.STUDENT);
        Department department = department();
        student.setDepartment(department);
        return offer(student, user(UserRole.COMPANY_MENTOR), company(), department);
    }

    private PlacementOffer offer(
            User student, User mentor, Company company, Department department) {
        InternshipTerm term =
                InternshipTerm.builder().code("T1").termName("Term").build();
        term.setId(UUID.randomUUID());
        JobPosition job =
                JobPosition.builder()
                        .company(company)
                        .term(term)
                        .title("Intern")
                        .workFormat(WorkFormat.ONSITE)
                        .location("CT")
                        .description("Description")
                        .status(JobStatus.APPROVED)
                        .build();
        job.setId(UUID.randomUUID());
        Document cv = Document.builder().owner(student).documentType(DocumentType.CV).build();
        cv.setId(UUID.randomUUID());
        JobApplication application =
                JobApplication.builder()
                        .job(job)
                        .student(student)
                        .submittedCvDocument(cv)
                        .status(ApplicationStatus.OFFERED)
                        .build();
        application.setId(UUID.randomUUID());
        PlacementOffer value =
                PlacementOffer.builder()
                        .application(application)
                        .proposedMentor(mentor)
                        .startDate(LocalDate.now())
                        .endDate(LocalDate.now().plusMonths(3))
                        .expiresAt(OffsetDateTime.now().plusDays(1))
                        .status(OfferStatus.ACCEPTED)
                        .build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private User user(UserRole role) {
        User value =
                User.builder()
                        .email(UUID.randomUUID() + "@example.com")
                        .passwordHash("hash")
                        .fullName("User")
                        .role(role)
                        .build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private Company company() {
        Company value =
                Company.builder()
                        .companyName("Acme")
                        .taxCode(UUID.randomUUID().toString())
                        .address(Map.of())
                        .verificationStatus(VerificationStatus.VERIFIED)
                        .build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private Department department() {
        Department value =
                Department.builder()
                        .code(UUID.randomUUID().toString())
                        .name("CNTT")
                        .contactEmail("it@example.com")
                        .build();
        value.setId(UUID.randomUUID());
        return value;
    }
}
