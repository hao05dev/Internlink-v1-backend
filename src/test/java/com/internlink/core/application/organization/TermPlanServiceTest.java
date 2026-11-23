package com.internlink.core.application.organization;

import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.TermMilestone;
import com.internlink.core.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TermPlanServiceTest {
    private final InternshipTerm term = new InternshipTerm();
    private final OffsetDateTime d1 = OffsetDateTime.parse("2026-09-07T00:00:00+07:00");
    private final OffsetDateTime d2 = OffsetDateTime.parse("2026-09-18T23:59:00+07:00");

    private TermPlanService.MilestoneInput input(String kind, String label, OffsetDateTime s, OffsetDateTime e, Integer w) {
        return new TermPlanService.MilestoneInput(kind, label, null, s, e, w);
    }

    @Test
    void normalisesKindAndKeepsOrder() {
        TermMilestone m = TermPlanService.validated(term, input(" acceptance_form_due ", "  Nộp phiếu  ", null, d2, null), 3);
        assertThat(m.getKind()).isEqualTo(TermMilestone.ACCEPTANCE_FORM_DUE);
        assertThat(m.getLabel()).isEqualTo("Nộp phiếu");
        assertThat(m.getSortOrder()).isEqualTo(3);
        assertThat(m.getTerm()).isSameAs(term);
    }

    @Test
    void missingKindBecomesOther() {
        assertThat(TermPlanService.validated(term, input(null, "Họp", d1, null, null), 0).getKind())
            .isEqualTo(TermMilestone.OTHER);
    }

    @Test
    void rejectsInvalidMilestones() {
        assertThatThrownBy(() -> TermPlanService.validated(term, input("FOO", "x", d1, null, null), 0))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> TermPlanService.validated(term, input("OTHER", " ", d1, null, null), 0))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> TermPlanService.validated(term, input("OTHER", "x", null, null, null), 0))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> TermPlanService.validated(term, input("OTHER", "x", d2, d1, null), 0))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> TermPlanService.validated(term, input("INTERNSHIP_PERIOD", "x", d1, d2, 10), 0))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptsWeekSpecificPeriod() {
        assertThat(TermPlanService.validated(term, input("INTERNSHIP_PERIOD", "TT 12 tuần", d1, d2, 12), 0)
            .getRequiredWeeks()).isEqualTo(12);
    }
}
