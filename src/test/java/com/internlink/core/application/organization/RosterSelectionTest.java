package com.internlink.core.application.organization;

import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RosterSelectionTest {

    private StudentRoster roster(UUID id) {
        StudentRoster roster = new StudentRoster();
        roster.setId(id);
        return roster;
    }

    @Test
    void nullSelectionKeepsWholeTerm() {
        assertThat(RosterSelection.of(null).test(roster(UUID.randomUUID()))).isTrue();
        assertThat(RosterSelection.isPartial(null)).isFalse();
    }

    @Test
    void selectionKeepsOnlyPickedRows() {
        UUID picked = UUID.randomUUID();
        Predicate<StudentRoster> selection = RosterSelection.of(List.of(picked));

        assertThat(selection.test(roster(picked))).isTrue();
        assertThat(selection.test(roster(UUID.randomUUID()))).isFalse();
        assertThat(RosterSelection.isPartial(List.of(picked))).isTrue();
    }

    @Test
    void emptySelectionIsRejected() {
        assertThatThrownBy(() -> RosterSelection.of(List.of()))
                .isInstanceOf(BadRequestException.class);
    }
}
