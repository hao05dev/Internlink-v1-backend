package com.internlink.core.application.placement.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.exception.BadRequestException;

class PlacementTransitionValidatorTest {

    private final PlacementTransitionValidator validator = new PlacementTransitionValidator(null, null);

    @Test
    void preparingPlacementCanBeTerminatedWhenStudentDropsBeforeStart() {
        InternshipPlacement placement = InternshipPlacement.builder().status(PlacementStatus.PREPARING).build();

        assertThatCode(() -> validator.validateTransition(placement, PlacementStatus.TERMINATED))
                .doesNotThrowAnyException();
    }

    @Test
    void preparingPlacementCannotSkipToCompletedOrTransferred() {
        InternshipPlacement placement = InternshipPlacement.builder().status(PlacementStatus.PREPARING).build();

        assertThatThrownBy(() -> validator.validateTransition(placement, PlacementStatus.COMPLETED))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validator.validateTransition(placement, PlacementStatus.TRANSFERRED))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void terminatedPlacementIsFinal() {
        InternshipPlacement placement = InternshipPlacement.builder().status(PlacementStatus.TERMINATED).build();

        assertThatThrownBy(() -> validator.validateTransition(placement, PlacementStatus.ACTIVE))
                .isInstanceOf(BadRequestException.class);
    }
}
