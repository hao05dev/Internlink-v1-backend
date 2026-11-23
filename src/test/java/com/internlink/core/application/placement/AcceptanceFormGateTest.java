package com.internlink.core.application.placement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.internlink.core.application.placement.impl.PlacementTransitionValidator;
import com.internlink.core.domain.evaluation.AssessmentScheme;
import com.internlink.core.domain.placement.AcceptanceFormStatus;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.StudentFoundApplication;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;

/** Placement chỉ được bắt đầu (ACTIVE) khi Phiếu tiếp nhận đã nằm trong danh sách Khoa công bố. */
class AcceptanceFormGateTest {

    private final PlacementTransitionValidator validator = new PlacementTransitionValidator(
            null, new AcceptanceFormGate(mock(JpaStudentFoundApplicationRepository.class)));

    @Test
    void placementCannotStartBeforeAcceptanceFormIsPublished() {
        for (String status : new String[] {
                AcceptanceFormStatus.SUBMITTED, AcceptanceFormStatus.RECEIVED, AcceptanceFormStatus.APPROVED}) {
            InternshipPlacement placement = placementWithForm(status);

            assertThatThrownBy(() -> validator.validateTransition(placement, PlacementStatus.ACTIVE))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("chưa công bố danh sách");
        }
    }

    @Test
    void placementStartsOnceAcceptanceFormIsPublished() {
        InternshipPlacement placement = placementWithForm(AcceptanceFormStatus.PUBLISHED);

        assertThatCode(() -> validator.validateTransition(placement, PlacementStatus.ACTIVE))
                .doesNotThrowAnyException();
    }

    private static InternshipPlacement placementWithForm(String formStatus) {
        StudentFoundApplication form = StudentFoundApplication.builder().status(formStatus).build();
        AssessmentScheme scheme = AssessmentScheme.builder().status("APPROVED").build();
        return InternshipPlacement.builder()
                .status(PlacementStatus.PREPARING)
                .source("OFFER")
                .assessmentScheme(scheme)
                .studentFoundApplication(form)
                .build();
    }
}
