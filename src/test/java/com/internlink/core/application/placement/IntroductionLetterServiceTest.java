package com.internlink.core.application.placement;

import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.IntroductionLetterRequest;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaIntroductionLetterRequestRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.placement.dto.request.ReviewIntroductionLetterRequest;
import com.internlink.core.shared.enums.IntroductionLetterStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntroductionLetterServiceTest {
    @Mock JpaIntroductionLetterRequestRepository letterRequests;
    @Mock JpaInternshipTermRepository terms;
    @Mock JpaStudentRosterRepository rosters;
    @Mock JpaUserRepository users;
    @Mock SecurityGuard security;
    @Mock NotificationService notifications;

    IntroductionLetterService service;
    Department ownDepartment;
    Department otherDepartment;
    InternshipTerm term;
    User student;

    @BeforeEach
    void setUp() {
        service = new IntroductionLetterService(letterRequests, terms, rosters, users, security, notifications,
                new com.internlink.core.application.organization.TermDepartmentScope(rosters,
                        org.mockito.Mockito.mock(com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository.class),
                        org.mockito.Mockito.mock(com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository.class)));
        ownDepartment = department("CICT");
        otherDepartment = department("CEN");
        term = InternshipTerm.builder().code("HK1").termName("Học kỳ 1").build();
        term.setId(UUID.randomUUID());
        student = user(UserRole.STUDENT, ownDepartment);
        lenient().when(rosters.findByTermIdAndClaimedUserId(any(), any())).thenReturn(Optional.empty());
        lenient().when(letterRequests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void facultyAdminCannotReviewLettersBecauseTheSchoolOfficeIssuesThem() {
        var request = pendingRequest();
        actAs(user(UserRole.FACULTY_ADMIN, ownDepartment));

        assertThatThrownBy(() -> service.review(request.getId(),
                new ReviewIntroductionLetterRequest(IntroductionLetterStatus.APPROVED, null, null)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(request.getStatus()).isEqualTo(IntroductionLetterStatus.PENDING);
        verify(letterRequests, never()).save(any());
    }

    @Test
    void facultyAdminCannotConfirmReceived() {
        var request = pendingRequest();
        request.setStatus(IntroductionLetterStatus.APPROVED);
        actAs(user(UserRole.FACULTY_ADMIN, ownDepartment));

        assertThatThrownBy(() -> service.confirmReceived(request.getId())).isInstanceOf(ForbiddenException.class);
        assertThat(request.getStatus()).isEqualTo(IntroductionLetterStatus.APPROVED);
    }

    @Test
    void schoolOfficeApprovesThenIssues() {
        var request = pendingRequest();
        actAs(user(UserRole.SCHOOL_OFFICE, null));

        service.review(request.getId(), new ReviewIntroductionLetterRequest(IntroductionLetterStatus.APPROVED, null, null));
        assertThat(request.getStatus()).isEqualTo(IntroductionLetterStatus.APPROVED);
        String approvedCode = request.getIssuedCode();
        assertThat(approvedCode).startsWith("GGT-HK1-");

        service.review(request.getId(), new ReviewIntroductionLetterRequest(IntroductionLetterStatus.ISSUED, null, null));
        assertThat(request.getStatus()).isEqualTo(IntroductionLetterStatus.ISSUED);
        assertThat(request.getIssuedCode()).isEqualTo(approvedCode);
        assertThat(request.getReceivedAt()).isNotNull();
    }

    @Test
    void pendingRequestCannotJumpStraightToIssued() {
        var request = pendingRequest();
        actAs(user(UserRole.SCHOOL_OFFICE, null));

        assertThatThrownBy(() -> service.review(request.getId(),
                new ReviewIntroductionLetterRequest(IntroductionLetterStatus.ISSUED, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThat(request.getStatus()).isEqualTo(IntroductionLetterStatus.PENDING);
    }

    @Test
    void finalStatesCannotBeReopened() {
        actAs(user(UserRole.SCHOOL_OFFICE, null));
        for (var finalStatus : List.of(IntroductionLetterStatus.ISSUED, IntroductionLetterStatus.REJECTED, IntroductionLetterStatus.CANCELLED)) {
            var request = pendingRequest();
            request.setStatus(finalStatus);
            assertThatThrownBy(() -> service.review(request.getId(),
                    new ReviewIntroductionLetterRequest(IntroductionLetterStatus.APPROVED, null, null)))
                    .isInstanceOf(BadRequestException.class);
            assertThat(request.getStatus()).isEqualTo(finalStatus);
        }
    }

    @Test
    void cannotRevertToPendingOrCancelledThroughReview() {
        var request = pendingRequest();
        actAs(user(UserRole.SCHOOL_OFFICE, null));

        assertThatThrownBy(() -> service.review(request.getId(),
                new ReviewIntroductionLetterRequest(IntroductionLetterStatus.CANCELLED, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listingByTermIsLimitedToThatDepartment() {
        when(terms.findById(term.getId())).thenReturn(Optional.of(term));
        var request = pendingRequest();
        when(letterRequests.findByTermIdOrderByCreatedAtDesc(term.getId())).thenReturn(List.of(request));

        actAs(user(UserRole.FACULTY_ADMIN, otherDepartment));
        assertThat(service.byTerm(term.getId())).isEmpty();

        actAs(user(UserRole.LECTURER, otherDepartment));
        assertThat(service.byTerm(term.getId())).isEmpty();

        actAs(user(UserRole.FACULTY_ADMIN, ownDepartment));
        assertThat(service.byTerm(term.getId())).hasSize(1);
    }

    @Test
    void listingByTermAllowedForSameDepartmentFacultyAndLecturer() {
        when(terms.findById(term.getId())).thenReturn(Optional.of(term));
        when(letterRequests.findByTermIdOrderByCreatedAtDesc(term.getId())).thenReturn(List.of());

        actAs(user(UserRole.FACULTY_ADMIN, ownDepartment));
        assertThat(service.byTerm(term.getId())).isEmpty();

        actAs(user(UserRole.LECTURER, ownDepartment));
        assertThat(service.byTerm(term.getId())).isEmpty();

        actAs(user(UserRole.SCHOOL_OFFICE, null));
        assertThat(service.byTerm(term.getId())).isEmpty();
    }

    @Test
    void schoolOfficeCanReviewAndConfirmReceivedForAnyDepartment() {
        var request = pendingRequest();
        when(letterRequests.save(any())).thenAnswer(inv -> inv.getArgument(0));

        actAs(user(UserRole.SCHOOL_OFFICE, null));
        var reviewed = service.review(request.getId(), new ReviewIntroductionLetterRequest(IntroductionLetterStatus.APPROVED, "GGT-001", null));
        assertThat(reviewed.status()).isEqualTo(IntroductionLetterStatus.APPROVED);

        var issued = service.confirmReceived(request.getId());
        assertThat(issued.status()).isEqualTo(IntroductionLetterStatus.ISSUED);
    }

    @Test
    void toggleRosterLetterReceivedFlipsStatusAndSetsTimestamp() {
        StudentRoster roster = StudentRoster.builder()
                .term(term)
                .studentCode("B2110940")
                .fullName("Lê Hoàng Nam")
                .officialEmail("b2110940@student.ctu.edu.vn")
                .academicYear("2026-2027")
                .letterReceived(false)
                .build();
        roster.setId(UUID.randomUUID());

        when(rosters.findById(roster.getId())).thenReturn(Optional.of(roster));
        when(rosters.save(any(StudentRoster.class))).thenAnswer(invocation -> invocation.getArgument(0));
        actAs(user(UserRole.SCHOOL_OFFICE, null));

        var response = service.toggleRosterLetterReceived(roster.getId());
        assertThat(response.letterReceived()).isTrue();
        assertThat(response.letterReceivedAt()).isNotNull();
        assertThat(roster.isLetterReceived()).isTrue();

        var flipped = service.toggleRosterLetterReceived(roster.getId());
        assertThat(flipped.letterReceived()).isFalse();
        assertThat(flipped.letterReceivedAt()).isNull();
        assertThat(roster.isLetterReceived()).isFalse();
    }

    private IntroductionLetterRequest pendingRequest() {
        var request = IntroductionLetterRequest.builder()
                .student(student).term(term).targetCompanyName("Đơn vị X")
                .status(IntroductionLetterStatus.PENDING).build();
        request.setId(UUID.randomUUID());
        lenient().when(letterRequests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        return request;
    }

    private void actAs(User actor) {
        lenient().when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        lenient().when(security.currentUser()).thenReturn(CustomUserDetail.create(actor));
    }

    private static Department department(String code) {
        Department department = Department.builder().code(code).name(code).build();
        department.setId(UUID.randomUUID());
        return department;
    }

    private static User user(UserRole role, Department department) {
        User user = User.builder().role(role).fullName(role.name()).department(department).build();
        user.setId(UUID.randomUUID());
        return user;
    }
}
