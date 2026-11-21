package com.internlink.core.infrastructure.persistence.jdbc;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thông tin CÔNG KHAI về các kỳ thực tập do Văn phòng Trường công bố: mốc thời gian, quy định chung của kỳ,
 * biểu mẫu bắt buộc. Không đọc bất kỳ bảng nào chứa dữ liệu cá nhân (roster, hồ sơ, điểm…).
 * Kỳ còn ở trạng thái DRAFT chưa được công bố nên bị loại.
 */
@Repository
@RequiredArgsConstructor
public class AssistantTermInfoRepository {
    private final JdbcTemplate jdbc;

    public record Term(UUID id, String code, String name, String academicYear, String semester, String status,
                       OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt,
                       OffsetDateTime applicationDeadline, LocalDate startDate, LocalDate endDate,
                       OffsetDateTime evaluationDeadline, String settingsJson) {}

    public List<Term> publishedTerms(int limit) {
        return jdbc.query("SELECT id, code, term_name, academic_year, semester, status, registration_open_at,"
                        + " registration_close_at, application_deadline, start_date, end_date, evaluation_deadline,"
                        + " settings::text FROM internship_terms WHERE status <> 'DRAFT'"
                        + " ORDER BY start_date DESC NULLS LAST LIMIT ?",
                (rs, i) -> new Term(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getObject(7, OffsetDateTime.class),
                        rs.getObject(8, OffsetDateTime.class), rs.getObject(9, OffsetDateTime.class),
                        rs.getObject(10, LocalDate.class), rs.getObject(11, LocalDate.class),
                        rs.getObject(12, OffsetDateTime.class), rs.getString(13)), limit);
    }

    /** Tên các biểu mẫu đang hiệu lực của từng kỳ (mỗi kỳ tối đa 20), khoá theo term_id. */
    public Map<UUID, List<String>> activeForms(List<UUID> termIds) {
        Map<UUID, List<String>> forms = new LinkedHashMap<>();
        if (termIds.isEmpty()) return forms;
        String placeholders = String.join(",", termIds.stream().map(id -> "?").toList());
        jdbc.query("SELECT term_id, title, is_mandatory FROM report_template_versions"
                        + " WHERE is_active AND term_id IN (" + placeholders + ") ORDER BY term_id, report_type, title",
                rs -> {
                    List<String> list = forms.computeIfAbsent(rs.getObject(1, UUID.class), k -> new java.util.ArrayList<>());
                    if (list.size() < 20) list.add(rs.getString(2) + (rs.getBoolean(3) ? " (bắt buộc)" : ""));
                }, termIds.toArray());
        return forms;
    }
}
