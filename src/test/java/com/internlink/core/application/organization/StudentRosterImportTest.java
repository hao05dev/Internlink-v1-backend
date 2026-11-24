package com.internlink.core.application.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.internlink.core.application.organization.impl.RosterAttemptHistoryResolver;
import com.internlink.core.application.organization.impl.StudentRosterAccountProvisioner;
import com.internlink.core.application.organization.impl.StudentRosterMapper;
import com.internlink.core.application.organization.impl.StudentRosterServiceImpl;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.infrastructure.persistence.jpa.JpaAcademicProgramRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.organization.dto.request.StudentRosterImportItem;
import com.internlink.core.presentation.organization.dto.response.RosterImportResponse;
import com.internlink.core.shared.enums.RosterSource;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.security.SecurityGuard;

/** VP Trường nắm danh sách SV thực tập của cả Trường; Khoa chỉ bổ sung SV ngành của khoa mình. */
@ExtendWith(MockitoExtension.class)
class StudentRosterImportTest {
    @Mock private JpaStudentRosterRepository rosterRepository;
    @Mock private JpaInternshipTermRepository termRepository;
    @Mock private JpaAcademicProgramRepository programRepository;
    @Mock private JpaUserRepository userRepository;
    @Mock private SecurityGuard securityGuard;
    @Mock private AuditLogService auditLogService;
    @Mock private RosterImportNotifier notifier;

    private StudentRosterServiceImpl service;
    private final Department cnpm = department("CNPM");
    private final Department khmt = department("KHMT");
    private final AcademicProgram se = program(cnpm, "SE");
    private final AcademicProgram cs = program(khmt, "CS");
    private final InternshipTerm term = term();

    @BeforeEach
    void setUp() {
        service = new StudentRosterServiceImpl(rosterRepository, termRepository, programRepository, userRepository,
                securityGuard, auditLogService, new StudentRosterMapper(), mock(StudentRosterAccountProvisioner.class),
                mock(RosterAttemptHistoryResolver.class),
                new TermDepartmentScope(rosterRepository, mock(JpaInternshipPlacementRepository.class),
                        mock(JpaStudentFoundApplicationRepository.class)),
                notifier);
        lenient().when(termRepository.findById(term.getId())).thenReturn(Optional.of(term));
        lenient().when(programRepository.findById(se.getId())).thenReturn(Optional.of(se));
        lenient().when(programRepository.findById(cs.getId())).thenReturn(Optional.of(cs));
        lenient().when(rosterRepository.findByTermIdAndStudentCode(any(), anyString())).thenReturn(Optional.empty());
        lenient().when(rosterRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void schoolOfficeImportsStudentsOfEveryDepartmentAndNotifiesThem() {
        User office = actor(UserRole.SCHOOL_OFFICE, null);

        RosterImportResponse result = service.importRosterList(term.getId(), List.of(item(se, "B01"), item(cs, "B02")));

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.rosters()).extracting("departmentName").containsExactly("CNPM", "KHMT");
        assertThat(result.rosters()).extracting("source").containsOnly("SCHOOL_OFFICE");
        assertThat(result.rosters()).extracting("addedByName").containsOnly(office.getFullName());
        verify(notifier).schoolOfficeImported(eq(term), anyList());
        verify(notifier, never()).facultyAdded(any(), any(), any(Integer.class));
    }

    @Test
    void facultyAddsOwnStudentMarkedAsFacultySourceAndNotifiesSchoolOffice() {
        User faculty = actor(UserRole.FACULTY_ADMIN, cnpm);

        RosterImportResponse result = service.importRosterList(term.getId(), List.of(item(se, "B03")));

        assertThat(result.rosters()).extracting("source").containsExactly("FACULTY");
        verify(notifier).facultyAdded(term, faculty, 1);
    }

    @Test
    void facultyCannotImportProgramOfAnotherDepartment() {
        actor(UserRole.FACULTY_ADMIN, cnpm);

        assertThatThrownBy(() -> service.importRosterList(term.getId(), List.of(item(cs, "B04"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thuộc khoa");
    }

    @Test
    void facultyKeepsSchoolOfficeProfileAndOnlyUpdatesLecturer() {
        actor(UserRole.FACULTY_ADMIN, cnpm);
        StudentRoster existing = roster(se, "B05", RosterSource.SCHOOL_OFFICE);
        when(rosterRepository.findByTermIdAndStudentCode(term.getId(), "B05")).thenReturn(Optional.of(existing));
        StudentRosterImportItem row = item(se, "B05");
        row.setFullName("Tên khác");
        row.setLecturerName("GV Mới");

        RosterImportResponse result = service.importRosterList(term.getId(), List.of(row));

        assertThat(existing.getFullName()).isEqualTo("Tên VPT");
        assertThat(existing.getLecturerName()).isEqualTo("GV Mới");
        assertThat(existing.getSource()).isEqualTo(RosterSource.SCHOOL_OFFICE);
        assertThat(result.created()).isZero();
        assertThat(result.skipped()).extracting("studentCode").containsExactly("B05");
        verify(notifier).facultyAdded(any(), any(), eq(0));
    }

    @Test
    void schoolOfficeReimportTakesOverFacultyRowWithoutClearingBlankCells() {
        actor(UserRole.SCHOOL_OFFICE, null);
        StudentRoster existing = roster(se, "B06", RosterSource.FACULTY);
        existing.setClassCode("DI21V7A1");
        existing.setPhoneNumber("0909");
        when(rosterRepository.findByTermIdAndStudentCode(term.getId(), "B06")).thenReturn(Optional.of(existing));
        StudentRosterImportItem row = item(se, "B06");
        row.setFullName("Tên chuẩn");

        RosterImportResponse result = service.importRosterList(term.getId(), List.of(row));

        assertThat(existing.getSource()).isEqualTo(RosterSource.SCHOOL_OFFICE);
        assertThat(existing.getFullName()).isEqualTo("Tên chuẩn");
        assertThat(existing.getClassCode()).isEqualTo("DI21V7A1");
        assertThat(existing.getPhoneNumber()).isEqualTo("0909");
        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    void facultyCannotTouchStudentListedUnderAnotherDepartment() {
        actor(UserRole.FACULTY_ADMIN, cnpm);
        StudentRoster existing = roster(cs, "B07", RosterSource.SCHOOL_OFFICE);
        when(rosterRepository.findByTermIdAndStudentCode(term.getId(), "B07")).thenReturn(Optional.of(existing));

        RosterImportResponse result = service.importRosterList(term.getId(), List.of(item(se, "B07")));

        assertThat(existing.getProgram()).isSameAs(cs);
        assertThat(result.rosters()).isEmpty();
        assertThat(result.skipped()).extracting("reason").first().asString().contains("khoa khác");
    }

    private User actor(UserRole role, Department department) {
        User user = User.builder().email(UUID.randomUUID() + "@ctu.edu.vn").passwordHash("x")
                .fullName(role.name()).role(role).department(department).build();
        user.setId(UUID.randomUUID());
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private StudentRosterImportItem item(AcademicProgram program, String code) {
        return StudentRosterImportItem.builder().programId(program.getId()).studentCode(code)
                .officialEmail(code.toLowerCase() + "@student.ctu.edu.vn").fullName("SV " + code)
                .academicYear("K47").internshipCourseCode("CT471").gpa(new BigDecimal("3.10")).build();
    }

    private StudentRoster roster(AcademicProgram program, String code, RosterSource source) {
        StudentRoster value = StudentRoster.builder().term(term).program(program).studentCode(code)
                .fullName("Tên VPT").officialEmail(code + "@student.ctu.edu.vn").source(source).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private static Department department(String code) {
        Department value = Department.builder().code(code).name(code).contactEmail(code + "@ctu.edu.vn").build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private static AcademicProgram program(Department department, String code) {
        AcademicProgram value = AcademicProgram.builder().department(department).code(code).name(code).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private static InternshipTerm term() {
        InternshipTerm value = InternshipTerm.builder().code("HK2").termName("HK2").build();
        value.setId(UUID.randomUUID());
        return value;
    }
}
