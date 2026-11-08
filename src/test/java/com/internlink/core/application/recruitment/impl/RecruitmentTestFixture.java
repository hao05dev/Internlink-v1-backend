package com.internlink.core.application.recruitment.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.internlink.core.application.placement.PlacementEligibilityService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.recruitment.JobApplication;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.recruitment.PlacementOffer;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentProfileRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.recruitment.dto.request.JobApplicationRequest;
import com.internlink.core.presentation.recruitment.dto.request.JobPositionRequest;
import com.internlink.core.presentation.recruitment.dto.request.PlacementOfferRequest;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
abstract class RecruitmentTestFixture {
    @Mock JpaJobPositionRepository jobRepository;
    @Mock JpaCompanyRepository companyRepository;
    @Mock JpaInternshipTermRepository termRepository;
    @Mock JpaDepartmentRepository departmentRepository;
    @Mock JpaUserRepository userRepository;
    @Mock JpaJobApplicationRepository applicationRepository;
    @Mock JpaDocumentRepository documentRepository;
    @Mock JpaPlacementOfferRepository offerRepository;
    @Mock JpaInternshipPlacementRepository placementRepository;
    @Mock JpaStudentProfileRepository studentProfileRepository;
    @Mock JpaStudentRosterRepository studentRosterRepository;
    @Mock SecurityGuard securityGuard;
    @Mock AuditLogService auditLogService;
    @Mock NotificationService notificationService;
    @Mock JpaJobSkillRepository jobSkillRepository;
    @Mock JpaSkillTaxonomyRepository taxonomyRepository;

    protected JobPositionServiceImpl jobService;
    protected JobApplicationServiceImpl applicationService;
    protected PlacementOfferServiceImpl offerService;
    protected PlacementEligibilityService placementEligibility;

    @BeforeEach
    void setUp() {
        JobPositionMapper mapper = new JobPositionMapper(new com.internlink.core.application.company.impl.CompanyPublicMapper(), new JobLocationResolver(), jobSkillRepository);
        JobSkillAssignmentHandler skillHandler =
                new JobSkillAssignmentHandler(jobSkillRepository, taxonomyRepository);
        JobPositionNotificationHelper notifHelper =
                new JobPositionNotificationHelper(
                        notificationService, userRepository, studentRosterRepository);
        jobService = new JobPositionServiceImpl(
            new JobPositionQueryService(
                jobRepository,
                mapper,
                new com.internlink.core.shared.api.PaginatedResponseMapper(),
                new com.internlink.core.application.recruitment.JobPositionAccessPolicy(userRepository, securityGuard),
                applicationRepository),
            new JobPositionWriteService(
                new JobLocationResolver(),
                jobRepository,
                companyRepository,
                termRepository,
                departmentRepository,
                userRepository,
                jobSkillRepository,
                securityGuard,
                mapper,
                skillHandler,
                mock(com.internlink.core.application.system.upload.MediaReferencePolicy.class),
                new com.internlink.core.application.recruitment.JobPositionAccessPolicy(userRepository, securityGuard)),
            new JobPositionLifecycleService(
                jobRepository,
                userRepository,
                jobSkillRepository,
                auditLogService,
                securityGuard,
                mapper,
                notifHelper,
                new com.internlink.core.application.recruitment.JobPositionAccessPolicy(userRepository, securityGuard)));

        applicationService =
                new JobApplicationServiceImpl(
                        applicationRepository,
                        jobRepository,
                        userRepository,
                        securityGuard,
                        notificationService,
                        new com.internlink.core.shared.api.PaginatedResponseMapper(),
                        new InterviewInvitationNotifier(
                                notificationService,
                                null,
                                null,
                                new com.internlink.core.infrastructure.integration.email.EmailProperties()),
                        new JobApplicationSubmissionValidator(
                                applicationRepository, documentRepository, studentProfileRepository,
                                studentRosterRepository, offerRepository),
                        new JobApplicationMapper());

        placementEligibility = new PlacementEligibilityService(
                placementRepository, studentRosterRepository, offerRepository);

        offerService =
                new PlacementOfferServiceImpl(
                        offerRepository,
                        applicationRepository,
                        jobRepository,
                        studentRosterRepository,
                        userRepository,
                        placementEligibility,
                        securityGuard,
                        auditLogService,
                        notificationService);

        lenient()
                .when(jobRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(jobRepository.findByIdForUpdate(any()))
                .thenAnswer(invocation -> jobRepository.findById(invocation.getArgument(0)));
        lenient()
                .when(applicationRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(offerRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    protected Department department() {
        Department value =
                Department.builder()
                        .code(UUID.randomUUID().toString())
                        .name("CNTT")
                        .contactEmail("it@example.com")
                        .build();
        value.setId(UUID.randomUUID());
        return value;
    }

    protected InternshipTerm term(Department department) {
        InternshipTerm value =
                InternshipTerm.builder()
                        .department(department)
                        .code("T1")
                        .termName("Term")
                        .academicYear("2026")
                        .semester("1")
                        .build();
        value.setId(UUID.randomUUID());
        return value;
    }

    protected Company company() {
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

    protected User user(UserRole role) {
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

    protected JobPosition job(
            Company company, InternshipTerm term, Department department, JobStatus status) {
        JobPosition value =
                JobPosition.builder()
                        .company(company)
                        .jobType(JobType.INTERNSHIP)
                        .term(term)
                        .department(department)
                        .title("Java Intern")
                        .workFormat(WorkFormat.HYBRID)
                        .location("CT")
                        .description("Description")
                        .vacancies(2)
                        .status(status)
                        .build();
        value.setId(UUID.randomUUID());
        return value;
    }

    protected JobApplication application(ApplicationStatus status) {
        User student = user(UserRole.STUDENT);
        Department department = department();
        JobPosition job = job(company(), term(department), department, JobStatus.APPROVED);
        Document cv = Document.builder().owner(student).documentType(DocumentType.CV).build();
        cv.setId(UUID.randomUUID());
        JobApplication value =
                JobApplication.builder()
                        .job(job)
                        .student(student)
                        .submittedCvDocument(cv)
                        .status(status)
                        .build();
        value.setId(UUID.randomUUID());
        return value;
    }

    protected JobPositionRequest jobRequest(UUID companyId, UUID termId, UUID departmentId) {
        return JobPositionRequest.builder()
                .companyId(companyId)
                .jobType(JobType.INTERNSHIP)
                .termId(termId)
                .departmentId(departmentId)
                .title("Java Intern")
                .workFormat(WorkFormat.HYBRID)
                .location("Can Tho")
                .vacancies(2)
                .description("Build services")
                .build();
    }

    protected PlacementOfferRequest offerRequest(UUID applicationId) {
        return PlacementOfferRequest.builder()
                .applicationId(applicationId)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusMonths(3))
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .build();
    }
}
