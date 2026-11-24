package com.internlink.core.application.organization;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class TermPlanDocumentServiceTest {
    @Test
    void mapsAiReadingIntoDraftAndDropsInvalidWeeks() {
        Map<String, Object> m1 = new HashMap<>(Map.of("kind", "INTERNSHIP_PERIOD", "label", "Thực tập 12 tuần",
            "start_date", "2026-09-14", "end_date", "2026-12-05", "required_weeks", 12));
        Map<String, Object> m2 = new HashMap<>(Map.of("kind", "GRADING", "label", "Chấm điểm", "required_weeks", 10));
        m2.put("start_date", null);
        var reading = TermPlanDocumentService.toReading(Map.of("document_ref", "349/CNTT&TT-GV",
            "milestones", List.of(m1, m2), "warnings", List.of("Mốc 2: chưa có ngày"), "model", "g"));

        assertThat(reading.documentRef()).isEqualTo("349/CNTT&TT-GV");
        assertThat(reading.milestones()).hasSize(2);
        assertThat(reading.milestones().get(0).requiredWeeks()).isEqualTo(12);
        assertThat(reading.milestones().get(0).startDate()).isEqualTo("2026-09-14");
        assertThat(reading.milestones().get(1).requiredWeeks()).isNull();
        assertThat(reading.milestones().get(1).startDate()).isNull();
        assertThat(reading.warnings()).containsExactly("Mốc 2: chưa có ngày");
    }

    @Test
    void emptyReadingGivesEmptyDraft() {
        var reading = TermPlanDocumentService.toReading(Map.of());
        assertThat(reading.milestones()).isEmpty();
        assertThat(reading.documentRef()).isNull();
    }
}
