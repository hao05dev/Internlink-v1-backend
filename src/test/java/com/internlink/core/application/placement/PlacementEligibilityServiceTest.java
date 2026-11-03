package com.internlink.core.application.placement;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaPlacementOfferRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlacementEligibilityServiceTest {

    @Mock
    private JpaInternshipPlacementRepository placementRepository;

    @Mock
    private JpaStudentRosterRepository rosterRepository;

    @Mock
    private JpaPlacementOfferRepository offerRepository;

    private PlacementEligibilityService eligibilityService;

    private final UUID studentId = UUID.randomUUID();
    private final UUID termId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        eligibilityService = new PlacementEligibilityService(
                placementRepository, rosterRepository, offerRepository);
    }

    @Test
    void requireNoLivePlacement_WhenActivePlacementExists_ShouldThrow() {
        InternshipPlacement activePlacement = InternshipPlacement.builder()
                .status(PlacementStatus.ACTIVE)
                .build();
        when(placementRepository.findByTermIdAndStudentId(termId, studentId))
                .thenReturn(List.of(activePlacement));

        assertThatThrownBy(() -> eligibilityService.requireNoLivePlacement(studentId, termId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã có một vị trí thực tập đang hoạt động");
    }

    @Test
    void requireNoLivePlacement_WhenOnlyTransferredOrTerminatedExists_ShouldPass() {
        InternshipPlacement transferredPlacement = InternshipPlacement.builder()
                .status(PlacementStatus.TRANSFERRED)
                .build();
        InternshipPlacement terminatedPlacement = InternshipPlacement.builder()
                .status(PlacementStatus.TERMINATED)
                .build();
        when(placementRepository.findByTermIdAndStudentId(termId, studentId))
                .thenReturn(List.of(transferredPlacement, terminatedPlacement));

        // Should not throw
        eligibilityService.requireNoLivePlacement(studentId, termId);
    }

    @Test
    void requireEligibleRoster_WhenEligible_ShouldReturnRoster() {
        StudentRoster roster = StudentRoster.builder()
                .build();
        when(rosterRepository.findByTermIdAndClaimedUserId(termId, studentId))
                .thenReturn(Optional.of(roster));

        StudentRoster result = eligibilityService.requireEligibleRoster(studentId, termId);
        assertThat(result).isSameAs(roster);
    }

    @Test
    void requireActiveLecturer_WhenInactive_ShouldThrow() {
        Department dept = Department.builder().build();
        dept.setId(deptId);
        User lecturer = User.builder()
                .role(UserRole.LECTURER)
                .isActive(false)
                .department(dept)
                .build();

        assertThatThrownBy(() -> eligibilityService.requireActiveLecturer(lecturer, deptId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không hoạt động");
    }

    @Test
    void requireActiveLecturer_WhenDifferentDepartment_ShouldThrow() {
        Department deptA = Department.builder().build();
        deptA.setId(UUID.randomUUID());
        User lecturer = User.builder()
                .role(UserRole.LECTURER)
                .isActive(true)
                .department(deptA)
                .build();

        assertThatThrownBy(() -> eligibilityService.requireActiveLecturer(lecturer, deptId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thuộc đơn vị quản lý");
    }

    @Test
    void requireActiveLecturer_WhenValid_ShouldPass() {
        Department dept = Department.builder().build();
        dept.setId(deptId);
        User lecturer = User.builder()
                .role(UserRole.LECTURER)
                .isActive(true)
                .department(dept)
                .build();

        // Should not throw
        eligibilityService.requireActiveLecturer(lecturer, deptId);
    }
}
