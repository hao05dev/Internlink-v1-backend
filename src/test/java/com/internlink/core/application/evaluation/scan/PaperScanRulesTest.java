package com.internlink.core.application.evaluation.scan;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class PaperScanRulesTest {

    private static Map<String, Object> reading(Double... scores) {
        List<Map<String, Object>> criteria = new ArrayList<>();
        for (int i = 0; i < PaperScanRules.M03_CODES.size(); i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("code", PaperScanRules.M03_CODES.get(i));
            row.put("score", i < scores.length ? scores[i] : null);
            row.put("confidence", 0.95);
            criteria.add(row);
        }
        Map<String, Object> reading = new HashMap<>();
        reading.put("is_m03_form", true);
        reading.put("criteria", criteria);
        reading.put("student_name", "Nguyễn Văn Đạt");
        reading.put("student_code", "B2 398026");
        reading.put("has_stamp", true);
        reading.put("has_signature", true);
        return reading;
    }

    @Test
    void averageSkipsBlankCriteriaAndRoundsHalfUp() {
        assertThat(PaperScanRules.average(Arrays.asList(new BigDecimal("8"), null, new BigDecimal("9"), new BigDecimal("8"))))
                .isEqualByComparingTo("8.3");
        assertThat(PaperScanRules.average(Arrays.asList(new BigDecimal("8.5"), new BigDecimal("8"))))
                .isEqualByComparingTo("8.3");
        assertThat(PaperScanRules.average(Arrays.asList((BigDecimal) null))).isNull();
    }

    @Test
    void cleanReadingOnlyHasNoWarnings() {
        var r = reading(8d, 8d, 8d, 8d, 8d, 8d, 8d, 8d, 8d, 8d);
        r.put("total", 80);
        assertThat(PaperScanRules.warnings(r, "nguyen van  dat", "B2398026")).isEmpty();
    }

    @Test
    void flagsMismatchLowConfidenceAndMissingStamp() {
        var r = reading(null, null, null, 9d, 8d, 8d, 8d, 8d, 8d, 8d);
        r.put("total", 60);
        r.put("has_stamp", false);
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) r.get("criteria");
        rows.get(3).put("confidence", 0.4);
        var codes = PaperScanRules.warnings(r, "Trần Thị B", "B9999999").stream().map(w -> w.get("code")).toList();
        assertThat(codes).containsExactly("MISSING_SCORES", "LOW_CONFIDENCE", "TOTAL_MISMATCH",
                "STUDENT_CODE_MISMATCH", "STUDENT_NAME_MISMATCH", "NO_STAMP");
    }

    @Test
    void notM03StopsOtherChecks() {
        var r = reading();
        r.put("is_m03_form", false);
        assertThat(PaperScanRules.warnings(r, "A", "B")).extracting(w -> w.get("code")).containsExactly("NOT_M03");
    }

    @Test
    void editedFieldsCountsChangedAndFilledCells() {
        var ai = PaperScanRules.criteriaScores(reading(8d, 7d));
        Map<String, BigDecimal> confirmed = new HashMap<>(ai);
        confirmed.put("I.2", new BigDecimal("1.0"));
        confirmed.put("I.3", new BigDecimal("9"));
        confirmed.put("I.1", new BigDecimal("8.0"));
        assertThat(PaperScanRules.editedFields(ai, confirmed)).isEqualTo(2);
    }
}
