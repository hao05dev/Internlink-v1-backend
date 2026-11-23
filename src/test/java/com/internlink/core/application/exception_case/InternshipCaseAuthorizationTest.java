package com.internlink.core.application.exception_case;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.internlink.core.application.exception_case.impl.InternshipCaseServiceImpl;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.exception_case.InternshipCase;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipCaseRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.exception_case.dto.request.InternshipCaseRequest;
import com.internlink.core.shared.enums.CaseSeverity;
import com.internlink.core.shared.enums.CaseStatus;
import com.internlink.core.shared.enums.CaseType;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Kiểm tra quyền theo quan hệ (không chỉ theo vai trò) khi đọc/báo/xử lý case thực tập. */
@ExtendWith(MockitoExtension.class)
class InternshipCaseAuthorizationTest {

    @Mock private JpaInternshipCaseRepository caseRepository;
    @Mock private JpaInternshipPlacementRepository placementRepository;
    @Mock private JpaUserRepository userRepository;
    @Mock private SecurityGuard securityGuard;

    private InternshipCaseServiceImpl service;
    private Department deptA;
    private Department deptB;
    private User student;
    private User lecturer;
    private InternshipPlacement placement;
    private InternshipCase existingCase;

    @BeforeEach
    void setUp() {
        service = new InternshipCaseServiceImpl(caseRepository, placementRepository, userRepository, securityGuard);
        deptA = department();
        deptB = department();
        student = user(UserRole.STUDENT, deptA);
        lecturer = user(UserRole.LECTURER, deptA);
        Company company = Company.builder().companyName("Acme").taxCode("123").address(Map.of()).build();
        company.setId(UUID.randomUUID());
        placement = InternshipPlacement.builder().student(student).lecturer(lecturer).company(company).build();
        placement.setId(UUID.randomUUID());
        existingCase = InternshipCase.builder()
                .placement(placement)
                .caseType(CaseType.INCIDENT)
                .reportedByUser(student)
                .severity(CaseSeverity.HIGH)
                .summary("Sự cố")
                .status(CaseStatus.OPEN)
                .openedAt(OffsetDateTime.now())
                .build();
        existingCase.setId(UUID.randomUUID());
        lenient().when(placementRepository.findById(placement.getId())).thenReturn(Optional.of(placement));
        lenient().when(caseRepository.findById(existingCase.getId())).thenReturn(Optional.of(existingCase));
    }

    @Test
    void studentOwnerCanReadOwnCase() {
        actAs(student);
        assertThat(service.getCaseById(existingCase.getId()).getId()).isEqualTo(existingCase.getId());
    }

    @Test
    void otherStudentCannotReadCase() {
        actAs(user(UserRole.STUDENT, deptA));
        assertThatThrownBy(() -> service.getCaseById(existingCase.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void otherStudentCannotListCasesOfPlacement() {
        actAs(user(UserRole.STUDENT, deptA));
        assertThatThrownBy(() -> service.getCasesByPlacement(placement.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void lecturerNotSupervisingCannotReadCase() {
        actAs(user(UserRole.LECTURER, deptA));
        assertThatThrownBy(() -> service.getCaseById(existingCase.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void lecturerCheckDoesNotFailWhenPlacementHasNoLecturer() {
        placement.setLecturer(null);
        actAs(user(UserRole.LECTURER, deptA));
        assertThatThrownBy(() -> service.getCaseById(existingCase.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void studentCannotReportCaseOnSomeoneElsesPlacement() {
        User stranger = user(UserRole.STUDENT, deptA);
        when(userRepository.findById(stranger.getId())).thenReturn(Optional.of(stranger));
        InternshipCaseRequest request = InternshipCaseRequest.builder()
                .placementId(placement.getId())
                .caseType(CaseType.INCIDENT)
                .severity(CaseSeverity.LOW)
                .summary("Giả mạo")
                .build();

        assertThatThrownBy(() -> service.reportCase(stranger.getId(), request))
                .isInstanceOf(ForbiddenException.class);
        verify(caseRepository, never()).save(any());
    }

    @Test
    void facultyAdminOfOtherDepartmentCannotResolve() {
        User otherAdmin = user(UserRole.FACULTY_ADMIN, deptB);
        when(userRepository.findById(otherAdmin.getId())).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> service.resolveCase(existingCase.getId(), otherAdmin.getId(), CaseStatus.RESOLVED, Map.of()))
                .isInstanceOf(ForbiddenException.class);
        verify(caseRepository, never()).save(any());
    }

    @Test
    void facultyAdminOfSameDepartmentCanResolve() {
        User admin = user(UserRole.FACULTY_ADMIN, deptA);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(caseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.resolveCase(existingCase.getId(), admin.getId(), CaseStatus.RESOLVED, Map.of());

        assertThat(response.getStatus()).isEqualTo(CaseStatus.RESOLVED);
    }

    @Test
    void listIsScopedByDepartmentAndSupervision() {
        InternshipPlacement otherPlacement = InternshipPlacement.builder()
                .student(user(UserRole.STUDENT, deptB)).company(placement.getCompany()).build();
        otherPlacement.setId(UUID.randomUUID());
        InternshipCase otherCase = InternshipCase.builder()
                .placement(otherPlacement).caseType(CaseType.INCIDENT).reportedByUser(student)
                .severity(CaseSeverity.LOW).summary("Khoa khác").status(CaseStatus.OPEN)
                .openedAt(OffsetDateTime.now()).build();
        otherCase.setId(UUID.randomUUID());
        when(caseRepository.findAllByOrderByOpenedAtDesc()).thenReturn(List.of(existingCase, otherCase));

        actAs(user(UserRole.FACULTY_ADMIN, deptA));
        assertThat(service.getAllCases(null, null, null)).extracting("id").containsExactly(existingCase.getId());

        actAs(lecturer);
        assertThat(service.getAllCases(null, null, null)).extracting("id").containsExactly(existingCase.getId());

        actAs(user(UserRole.LECTURER, deptA));
        assertThat(service.getAllCases(null, null, null)).isEmpty();

        actAs(user(UserRole.ADMIN, null));
        assertThat(service.getAllCases(null, null, null)).hasSize(2);
    }

    private void actAs(User actor) {
        CustomUserDetail detail = mock(CustomUserDetail.class);
        lenient().when(detail.getId()).thenReturn(actor.getId());
        lenient().when(securityGuard.currentUser()).thenReturn(detail);
        lenient().when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
    }

    private static Department department() {
        Department value = Department.builder().build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private static User user(UserRole role, Department department) {
        User value = User.builder()
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .fullName("User " + role)
                .role(role)
                .department(department)
                .build();
        value.setId(UUID.randomUUID());
        return value;
    }
}
