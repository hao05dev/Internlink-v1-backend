package com.internlink.core.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import com.internlink.core.application.organization.impl.AcademicProgramServiceImpl;
import com.internlink.core.application.organization.impl.DepartmentServiceImpl;
import com.internlink.core.application.organization.impl.InternshipTermServiceImpl;
import com.internlink.core.application.organization.impl.RosterAttemptHistoryResolver;
import com.internlink.core.application.organization.impl.StudentRosterAccountProvisioner;
import com.internlink.core.application.organization.impl.StudentRosterMapper;
import com.internlink.core.application.organization.impl.StudentRosterServiceImpl;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.infrastructure.persistence.jpa.JpaAcademicProgramRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaAssessmentSchemeRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaDepartmentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentProfileRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.organization.dto.request.AcademicProgramRequest;
import com.internlink.core.presentation.organization.dto.request.DepartmentRequest;
import com.internlink.core.presentation.organization.dto.request.InternshipTermRequest;
import com.internlink.core.presentation.organization.dto.request.StudentRosterImportItem;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.security.SecurityGuard;

@ExtendWith(MockitoExtension.class)
class CoreOrganizationServicesTest {

    @Mock
    private JpaDepartmentRepository departmentRepository;

    @Mock
    private JpaAcademicProgramRepository programRepository;

    @Mock
    private JpaAssessmentSchemeRepository assessmentSchemeRepository;

    @Mock
    private JpaInternshipTermRepository termRepository;

    @Mock
    private JpaStudentRosterRepository rosterRepository;

    @Mock
    private JpaUserRepository userRepository;

    @Mock
    private JpaStudentProfileRepository profileRepository;

    @Mock
    private JpaInternshipPlacementRepository placementRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SecurityGuard securityGuard;

    @BeforeEach
    void setUp() {
        lenient()
                .when(departmentRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(programRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.clearContext();
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

    private AcademicProgram program(Department department) {
        AcademicProgram value =
                AcademicProgram.builder()
                        .department(department)
                        .code("SE")
                        .name("Software Engineering")
                        .build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private InternshipTerm term(Department department) {
        InternshipTerm value =
                InternshipTerm.builder().code("T1").termName("Term").build();
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

    @Test
    void departmentCreationNormalizesCodeAndEmail() {
        DepartmentServiceImpl service =
                new DepartmentServiceImpl(departmentRepository, auditLogService, securityGuard);
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(user(UserRole.ADMIN)));
        DepartmentRequest request =
                DepartmentRequest.builder()
                        .code("  cit ")
                        .name("  CNTT ")
                        .contactEmail("  IT@EXAMPLE.COM ")
                        .build();

        var response = service.createDepartment(request);

        assertThat(response.getCode()).isEqualTo("CIT");
        assertThat(response.getContactEmail()).isEqualTo("it@example.com");
    }

    @Test
    void academicProgramRejectsDuplicateCode() {
        AcademicProgramServiceImpl service =
                new AcademicProgramServiceImpl(
                        programRepository,
                        departmentRepository,
                        profileRepository,
                        rosterRepository,
                        assessmentSchemeRepository,
                        auditLogService,
                        securityGuard);
        Department department = department();
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(programRepository.existsByCode("SE")).thenReturn(true);
        AcademicProgramRequest request =
                AcademicProgramRequest.builder()
                        .departmentId(department.getId())
                        .code("se")
                        .name("Software Engineering")
                        .build();

        assertThatThrownBy(() -> service.createProgram(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void academicProgramUpdateAndRejectReferencedDelete() {
        AcademicProgramServiceImpl service =
                new AcademicProgramServiceImpl(
                        programRepository,
                        departmentRepository,
                        profileRepository,
                        rosterRepository,
                        assessmentSchemeRepository,
                        auditLogService,
                        securityGuard);
        Department department = department();
        AcademicProgram program = program(department);
        when(programRepository.findById(program.getId())).thenReturn(Optional.of(program));
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(user(UserRole.ADMIN)));

        AcademicProgramRequest updateRequest =
                AcademicProgramRequest.builder()
                        .departmentId(department.getId())
                        .code("SE_NEW")
                        .name("Software Engineering Advanced")
                        .track("REGULAR")
                        .isActive(true)
                        .build();

        var updated = service.updateProgram(program.getId(), updateRequest);
        assertThat(updated.getCode()).isEqualTo("SE_NEW");

        when(profileRepository.existsByProgram_Id(program.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteProgram(program.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void internshipTermRejectsReversedDates() {
        InternshipTermServiceImpl service =
                new InternshipTermServiceImpl(
                        termRepository,
                        placementRepository,
                        userRepository,
                        securityGuard,
                        auditLogService);
        User actor = user(UserRole.SCHOOL_OFFICE);
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(actor));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        InternshipTermRequest request =
                InternshipTermRequest.builder()
                        .code("T1")
                        .termName("Term")
                        .academicYear("2026")
                        .semester("1")
                        .registrationOpenAt(OffsetDateTime.now())
                        .registrationCloseAt(OffsetDateTime.now().plusDays(1))
                        .startDate(LocalDate.now().plusMonths(2))
                        .endDate(LocalDate.now().plusMonths(1))
                        .applicationDeadline(OffsetDateTime.now().plusDays(10))
                        .evaluationDeadline(OffsetDateTime.now().plusMonths(3))
                        .build();

        assertThatThrownBy(() -> service.createTerm(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rosterRejectsProgramFromAnotherDepartment() {
        StudentRosterServiceImpl service =
                new StudentRosterServiceImpl(
                        rosterRepository,
                        termRepository,
                        programRepository,
                        userRepository,
                        securityGuard,
                        auditLogService,
                        new StudentRosterMapper(),
                        mock(StudentRosterAccountProvisioner.class),
                        mock(RosterAttemptHistoryResolver.class),
                        new com.internlink.core.application.organization.TermDepartmentScope(rosterRepository, placementRepository, org.mockito.Mockito.mock(com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository.class)));
        Department termDepartment = department();
        User actor = user(UserRole.FACULTY_ADMIN);
        actor.setDepartment(termDepartment);
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(actor));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        Department otherDepartment = department();
        InternshipTerm term = term(termDepartment);
        AcademicProgram program = program(otherDepartment);
        when(termRepository.findById(term.getId())).thenReturn(Optional.of(term));
        when(programRepository.findById(program.getId())).thenReturn(Optional.of(program));
        StudentRosterImportItem item =
                StudentRosterImportItem.builder()
                        .programId(program.getId())
                        .studentCode("B20")
                        .officialEmail("student@example.com")
                        .fullName("Student")
                        .academicYear("2020")
                        .internshipCourseCode("CT518E")
                        .gpa(new java.math.BigDecimal("3.20"))
                        .build();

        assertThatThrownBy(() -> service.importRosterList(term.getId(), List.of(item)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rosterRejectsStudentWithoutGpa() {
        StudentRosterServiceImpl service =
                new StudentRosterServiceImpl(
                        rosterRepository,
                        termRepository,
                        programRepository,
                        userRepository,
                        securityGuard,
                        auditLogService,
                        new StudentRosterMapper(),
                        mock(StudentRosterAccountProvisioner.class),
                        mock(RosterAttemptHistoryResolver.class),
                        new com.internlink.core.application.organization.TermDepartmentScope(rosterRepository, placementRepository, org.mockito.Mockito.mock(com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository.class)));
        Department department = department();
        User actor = user(UserRole.FACULTY_ADMIN);
        actor.setDepartment(department);
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(actor));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        InternshipTerm term = term(department);
        when(termRepository.findById(term.getId())).thenReturn(Optional.of(term));
        StudentRosterImportItem item =
                StudentRosterImportItem.builder()
                        .programId(program(department).getId())
                        .studentCode("B20")
                        .officialEmail("student@example.com")
                        .fullName("Student")
                        .academicYear("2020")
                        .internshipCourseCode("CT518E")
                        .build();

        assertThatThrownBy(() -> service.importRosterList(term.getId(), List.of(item)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("GPA");
    }
}
