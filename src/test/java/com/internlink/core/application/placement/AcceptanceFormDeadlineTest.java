package com.internlink.core.application.placement;

import com.internlink.core.application.placement.AcceptanceFormDeadline.Outcome;
import com.internlink.core.domain.organization.TermMilestone;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptanceFormDeadlineTest {
    private static final OffsetDateTime DUE = OffsetDateTime.parse("2026-09-06T23:59:00+07:00");

    private static TermMilestone due(OffsetDateTime end, Integer weeks, String policy) {
        return TermMilestone.builder().kind(TermMilestone.ACCEPTANCE_FORM_DUE).label("Nộp phiếu")
            .endAt(end).requiredWeeks(weeks).latePolicy(policy).build();
    }

    @Test
    void lastMinuteOfTheDueDayIsStillOnTime() {
        var plan = List.of(due(DUE, null, TermMilestone.LOCK));
        assertThat(AcceptanceFormDeadline.decide(plan, 8, DUE.plusSeconds(30)).outcome()).isEqualTo(Outcome.ON_TIME);
        assertThat(AcceptanceFormDeadline.decide(plan, 8, DUE.plusMinutes(2)).outcome()).isEqualTo(Outcome.LOCKED);
    }

    @Test
    void lateIsTheDefaultPolicy() {
        var decision = AcceptanceFormDeadline.decide(List.of(due(DUE, null, TermMilestone.ALLOW_LATE)), 12, DUE.plusDays(1));
        assertThat(decision.outcome()).isEqualTo(Outcome.LATE);
        assertThat(decision.dueDay()).isEqualTo("06/09/2026");
    }

    @Test
    void courseSpecificDeadlineWinsOverTheGeneralOne() {
        var plan = List.of(due(DUE, null, TermMilestone.LOCK), due(DUE.plusDays(7), 12, TermMilestone.LOCK));
        assertThat(AcceptanceFormDeadline.decide(plan, 12, DUE.plusDays(3)).outcome()).isEqualTo(Outcome.ON_TIME);
        assertThat(AcceptanceFormDeadline.decide(plan, 8, DUE.plusDays(3)).outcome()).isEqualTo(Outcome.LOCKED);
    }

    @Test
    void planWithoutAcceptanceDeadlineNeverBlocks() {
        var other = TermMilestone.builder().kind(TermMilestone.GRADING).label("Chấm").endAt(DUE).build();
        assertThat(AcceptanceFormDeadline.decide(List.of(other), 8, DUE.plusDays(30)).outcome()).isEqualTo(Outcome.ON_TIME);
    }
}
