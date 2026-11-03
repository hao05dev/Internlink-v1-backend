package com.internlink.core.application.organization;

import com.internlink.core.application.organization.impl.RosterLecturerAssignmentServiceImpl;
import com.internlink.core.application.organization.impl.StudentRosterMapper;
import com.internlink.core.application.placement.PlacementEligibilityService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.TermStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.security.SecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RosterLecturerAssignmentServiceTest {
    @Mock JpaStudentRosterRepository rosters;
    @Mock JpaInternshipTermRepository terms;
    @Mock JpaUserRepository users;
    @Mock JpaInternshipPlacementRepository placements;
    @Mock SecurityGuard security;
    @Mock AuditLogService audit;
    @Mock NotificationService notifications;

    RosterLecturerAssignmentServiceImpl service;
    InternshipTerm term;
    User oldLecturer;
    User newLecturer;
    User student;
    StudentRoster roster;

    @BeforeEach
    void setUp() {
        service = new RosterLecturerAssignmentServiceImpl(rosters, terms, users, placements,
                new PlacementEligibilityService(placements, rosters, null), security, audit, notifications,
                new StudentRosterMapper());
        Department department = Department.builder().code("CICT").name("CNTT&TT").build();
        department.setId(UUID.randomUUID());
        User admin = user(UserRole.FACULTY_ADMIN, department, "Quản lý", "qltt@ctu.edu.vn");
        oldLecturer = user(UserRole.LECTURER, department, "GV Cũ", "old@ctu.edu.vn");
        newLecturer = user(UserRole.LECTURER, department, "GV Mới", "new@ctu.edu.vn");
        student = user(UserRole.STUDENT, department, "Sinh viên A", "a@student.ctu.edu.vn");
        term = InternshipTerm.builder().department(department).status(TermStatus.APPLICATION_OPEN).termName("HK2").build();
        term.setId(UUID.randomUUID());
        AcademicProgram program = AcademicProgram.builder().name("KTPM").department(department).build();
        program.setId(UUID.randomUUID());
        roster = StudentRoster.builder().term(term).program(program).studentCode("B2100001").fullName("Sinh viên A")
                .assignedLecturer(oldLecturer).lecturerCode("GV01").lecturerName("GV Cũ").lecturerEmail("old@ctu.edu.vn")
                .build();
        roster.setId(UUID.randomUUID());

        when(security.currentUser()).thenReturn(CustomUserDetail.create(admin));
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(users.findById(newLecturer.getId())).thenReturn(Optional.of(newLecturer));
        when(rosters.findById(roster.getId())).thenReturn(Optional.of(roster));
        when(placements.findByTermIdAndStudentId(any(), any())).thenReturn(List.of());
    }

    @Test
    void reassignsRosterWithoutPlacementAndDropsTheOldLecturerCode() {
        var response = service.assign(roster.getId(), newLecturer.getId(), null);

        assertThat(roster.getAssignedLecturer()).isSameAs(newLecturer);
        assertThat(roster.getLecturerCode()).isNull();
        assertThat(response.getLecturerEmail()).isEqualTo("new@ctu.edu.vn");
        verify(audit).logAction(any(), eq("REASSIGN_ROSTER_LECTURER"), eq("StudentRoster"), eq(roster.getId()),
                eq("SUCCESS"), any(), any());
        verify(notifications).sendNotification(eq(newLecturer.getId()), eq("LECTURER_ASSIGNED"), anyString(), anyString(), anyString());
        verify(notifications).sendNotification(eq(oldLecturer.getId()), eq("LECTURER_UNASSIGNED"), anyString(), anyString(), anyString());
    }

    @Test
    void reassigningAnInterningStudentNeedsAReasonAndMovesThePlacement() {
        roster.setClaimedUser(student);
        InternshipPlacement placement = InternshipPlacement.builder().student(student).term(term)
                .lecturer(oldLecturer).status(PlacementStatus.ACTIVE).build();
        placement.setId(UUID.randomUUID());
        when(placements.findByTermIdAndStudentId(term.getId(), student.getId())).thenReturn(List.of(placement));

        assertThatThrownBy(() -> service.assign(roster.getId(), newLecturer.getId(), " "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("lý do");
        assertThat(placement.getLecturer()).isSameAs(oldLecturer);

        service.assign(roster.getId(), newLecturer.getId(), "GV đi công tác");

        assertThat(placement.getLecturer()).isSameAs(newLecturer);
        assertThat(roster.getAssignedLecturer()).isSameAs(newLecturer);
        verify(placements).save(placement);
        verify(notifications).sendNotification(eq(student.getId()), eq("LECTURER_ASSIGNED"), anyString(), anyString(), anyString());
    }

    @Test
    void sameLecturerIsANoOp() {
        when(users.findById(oldLecturer.getId())).thenReturn(Optional.of(oldLecturer));

        service.assign(roster.getId(), oldLecturer.getId(), null);

        verify(rosters, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void unassignIsRefusedWhileTheStudentIsInterning() {
        roster.setClaimedUser(student);
        InternshipPlacement placement = InternshipPlacement.builder().student(student).term(term)
                .lecturer(oldLecturer).status(PlacementStatus.PREPARING).build();
        when(placements.findByTermIdAndStudentId(term.getId(), student.getId())).thenReturn(List.of(placement));

        assertThatThrownBy(() -> service.unassign(roster.getId())).isInstanceOf(BadRequestException.class);
        assertThat(roster.getAssignedLecturer()).isSameAs(oldLecturer);
    }

    @Test
    void unassignClearsTheImportedLecturer() {
        service.unassign(roster.getId());

        assertThat(roster.getAssignedLecturer()).isNull();
        assertThat(roster.getLecturerEmail()).isNull();
        assertThat(roster.getLecturerName()).isNull();
        verify(notifications).sendNotification(eq(oldLecturer.getId()), eq("LECTURER_UNASSIGNED"), anyString(), anyString(), anyString());
    }

    @Test
    void bulkAssignCountsChangesAndReportsRowsOutsideTheTerm() {
        StudentRoster unassigned = StudentRoster.builder().term(term).program(roster.getProgram())
                .studentCode("B2100002").fullName("Sinh viên B").build();
        unassigned.setId(UUID.randomUUID());
        when(terms.findById(term.getId())).thenReturn(Optional.of(term));
        when(rosters.findByTermIdOrderByStudentCodeAsc(term.getId())).thenReturn(List.of(roster, unassigned));

        var result = service.assignBulk(term.getId(), List.of(roster.getId(), unassigned.getId(), UUID.randomUUID()),
                newLecturer.getId(), null);

        assertThat(result.updated()).isEqualTo(2);
        assertThat(result.unchanged()).isZero();
        assertThat(result.failures()).containsExactly("1 dòng không thuộc học kỳ này");
        assertThat(unassigned.getAssignedLecturer()).isSameAs(newLecturer);
    }

    @Test
    void rejectsLecturerFromAnotherFaculty() {
        Department other = Department.builder().code("CE").build();
        other.setId(UUID.randomUUID());
        User outsider = user(UserRole.LECTURER, other, "GV khoa khác", "x@ctu.edu.vn");
        when(users.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> service.assign(roster.getId(), outsider.getId(), null))
                .isInstanceOf(BadRequestException.class);
    }

    private static User user(UserRole role, Department department, String name, String email) {
        User user = User.builder().role(role).department(department).fullName(name).email(email).isActive(true).build();
        user.setId(UUID.randomUUID());
        return user;
    }
}
