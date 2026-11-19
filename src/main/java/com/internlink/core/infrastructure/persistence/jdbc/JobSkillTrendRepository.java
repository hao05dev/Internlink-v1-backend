package com.internlink.core.infrastructure.persistence.jdbc;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Số liệu tổng hợp kỹ năng trên tin tuyển đã được Khoa duyệt, theo từng đợt thực tập.
 * Chỉ trả về số đếm, không có dữ liệu cá nhân nào.
 */
@Repository
@RequiredArgsConstructor
public class JobSkillTrendRepository {
    private static final String APPROVED = " j.status IN ('APPROVED','CLOSED') AND j.approved_at IS NOT NULL ";

    private final JdbcTemplate jdbc;

    public record TermTotal(UUID termId, String label, long totalJobs) {}

    /** Hai đợt gần nhất (mới nhất trước) có tin đã duyệt. */
    public List<TermTotal> latestTerms() {
        return jdbc.query("SELECT t.id, t.term_name, COUNT(j.id) AS total FROM internship_terms t"
                        + " JOIN job_positions j ON j.term_id = t.id WHERE" + APPROVED
                        + " GROUP BY t.id, t.term_name, t.start_date ORDER BY t.start_date DESC LIMIT 2",
                (rs, i) -> new TermTotal(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)));
    }

    /** Tên kỹ năng → số tin yêu cầu kỹ năng đó trong đợt, giảm dần. */
    public Map<String, Long> skillCounts(UUID termId, int limit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT s.skill_name, COUNT(DISTINCT j.id) AS c FROM job_skills js"
                        + " JOIN job_positions j ON j.id = js.job_id JOIN skill_taxonomies s ON s.id = js.skill_id"
                        + " WHERE j.term_id = ? AND" + APPROVED
                        + " GROUP BY s.skill_name ORDER BY c DESC, s.skill_name LIMIT ?",
                rs -> { counts.put(rs.getString(1), rs.getLong(2)); }, termId, limit);
        return counts;
    }

    /** Đếm theo một cột phân loại an toàn (work_format | job_type). */
    public Map<String, Long> countBy(UUID termId, String column) {
        if (!List.of("work_format", "job_type").contains(column)) throw new IllegalArgumentException(column);
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT j." + column + ", COUNT(*) FROM job_positions j WHERE j.term_id = ? AND" + APPROVED
                        + " GROUP BY j." + column + " ORDER BY 2 DESC",
                rs -> { counts.put(rs.getString(1), rs.getLong(2)); }, termId);
        return counts;
    }
}
