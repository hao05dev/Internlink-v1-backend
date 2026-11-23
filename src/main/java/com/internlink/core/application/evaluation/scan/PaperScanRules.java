package com.internlink.core.application.evaluation.scan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;

/** Quy tắc thuần cho phiếu M-TT-03: điểm trung bình và các cảnh báo cần GVHD soát lại. */
public final class PaperScanRules {
    public static final List<String> M03_CODES =
            List.of("I.1", "I.2", "I.3", "I.4", "II.1", "II.2", "II.3", "III.1", "III.2", "III.3");
    public static final List<String> CTDT_OPTIONS =
            List.of("FIT", "NOT_FIT", "SOFT_SKILLS", "FOREIGN_LANGUAGE", "TEAMWORK");
    static final double LOW_CONFIDENCE = 0.7;

    private PaperScanRules() {}

    /** Trung bình các tiêu chí có điểm (thực tập online được bỏ trống I.1–I.3), làm tròn 1 chữ số. */
    public static BigDecimal average(Collection<BigDecimal> scores) {
        List<BigDecimal> present = scores.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) return null;
        BigDecimal sum = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(present.size()), 1, RoundingMode.HALF_UP);
    }

    public static Map<String, BigDecimal> criteriaScores(Map<String, Object> reading) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map<String, Object> row : criteria(reading)) {
            Object score = row.get("score");
            result.put(String.valueOf(row.get("code")), score instanceof Number n ? new BigDecimal(n.toString()) : null);
        }
        return result;
    }

    public static List<Map<String, Object>> warnings(Map<String, Object> reading, String studentName, String studentCode) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Boolean.TRUE.equals(reading.get("is_m03_form"))) {
            out.add(warning("NOT_M03", "Ảnh có vẻ không phải Phiếu đánh giá M-TT-03.", List.of()));
            return out;
        }
        Map<String, BigDecimal> scores = criteriaScores(reading);
        List<String> missing = scores.entrySet().stream()
                .filter(e -> e.getValue() == null).map(Map.Entry::getKey).toList();
        if (!missing.isEmpty())
            out.add(warning("MISSING_SCORES",
                    "Có tiêu chí chưa có điểm (thực tập online được bỏ trống I.1–I.3).", missing));
        List<String> unsure = criteria(reading).stream()
                .filter(r -> r.get("score") != null && number(r.get("confidence")) < LOW_CONFIDENCE)
                .map(r -> String.valueOf(r.get("code"))).toList();
        if (!unsure.isEmpty())
            out.add(warning("LOW_CONFIDENCE", "Nét chữ khó đọc, cần đối chiếu với ảnh.", unsure));
        if (reading.get("total") instanceof Number total) {
            BigDecimal sum = scores.values().stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.subtract(new BigDecimal(total.toString())).abs().compareTo(new BigDecimal("0.05")) > 0)
                out.add(warning("TOTAL_MISMATCH", "Dòng Cộng trên phiếu (" + plain(new BigDecimal(total.toString()))
                        + ") khác tổng các điểm đọc được (" + plain(sum) + ").", List.of()));
        }
        String code = text(reading.get("student_code"));
        if (code != null && studentCode != null && !compact(code).equalsIgnoreCase(compact(studentCode)))
            out.add(warning("STUDENT_CODE_MISMATCH",
                    "MSSV trên phiếu (" + code + ") không khớp sinh viên của lần thực tập.", List.of()));
        String name = text(reading.get("student_name"));
        if (name != null && studentName != null && !fold(name).equals(fold(studentName)))
            out.add(warning("STUDENT_NAME_MISMATCH", "Họ tên trên phiếu (" + name + ") không khớp sinh viên.", List.of()));
        if (!Boolean.TRUE.equals(reading.get("has_stamp")))
            out.add(warning("NO_STAMP", "Không thấy mộc đỏ của cơ quan.", List.of()));
        if (!Boolean.TRUE.equals(reading.get("has_signature")))
            out.add(warning("NO_SIGNATURE", "Không thấy chữ ký trên phiếu.", List.of()));
        return out;
    }

    /** Số tiêu chí GVHD sửa so với AI — đo độ chính xác mà không cần gán nhãn tay. */
    public static int editedFields(Map<String, BigDecimal> ai, Map<String, BigDecimal> confirmed) {
        int edited = 0;
        for (String code : M03_CODES) {
            BigDecimal a = ai.get(code);
            BigDecimal b = confirmed.get(code);
            if (a == null ? b != null : b == null || a.compareTo(b) != 0) edited++;
        }
        return edited;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> criteria(Map<String, Object> reading) {
        return reading.get("criteria") instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(o -> (Map<String, Object>) o).toList()
                : List.of();
    }

    private static Map<String, Object> warning(String code, String message, List<String> fields) {
        return Map.of("code", code, "message", message, "fields", fields);
    }

    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    private static String compact(String value) {
        return value.replaceAll("\\s+", "");
    }

    static String fold(String value) {
        String noMarks = Normalizer.normalize(value.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noMarks.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
