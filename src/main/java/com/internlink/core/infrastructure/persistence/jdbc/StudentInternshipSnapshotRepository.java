package com.internlink.core.infrastructure.persistence.jdbc;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ảnh chụp tiến độ thực tập của CHÍNH một sinh viên (lọc cứng theo userId của phiên đăng nhập).
 * Không đọc tên/email của giảng viên, mentor hay sinh viên khác.
 */
@Repository
@RequiredArgsConstructor
public class StudentInternshipSnapshotRepository {
    private final JdbcTemplate jdbc;

    public record Enrollment(String termName, String studentCode, boolean letterReceived) {}

    public record Placement(UUID id, String companyName, String status, LocalDate startDate, LocalDate endDate) {}

    public Optional<Enrollment> latestEnrollment(UUID userId) {
        return jdbc.query("SELECT t.term_name, r.student_code, r.letter_received FROM student_rosters r"
                        + " JOIN internship_terms t ON t.id = r.term_id WHERE r.claimed_user_id = ?"
                        + " ORDER BY t.start_date DESC NULLS LAST LIMIT 1",
                (rs, i) -> new Enrollment(rs.getString(1), rs.getString(2), rs.getBoolean(3)), userId)
                .stream().findFirst();
    }

    public Map<String, Long> applicationCounts(UUID userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) FROM job_applications WHERE student_id = ? GROUP BY status",
                rs -> { counts.put(rs.getString(1), rs.getLong(2)); }, userId);
        return counts;
    }

    public Optional<Placement> latestPlacement(UUID userId) {
        List<Placement> rows = jdbc.query("SELECT p.id, c.company_name, p.status, p.start_date, p.end_date"
                        + " FROM internship_placements p JOIN companies c ON c.id = p.company_id"
                        + " WHERE p.student_id = ? ORDER BY p.start_date DESC NULLS LAST, p.created_at DESC LIMIT 1",
                (rs, i) -> new Placement(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class)), userId);
        return rows.stream().findFirst();
    }

    public Map<String, Long> logbookCounts(UUID placementId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) FROM weekly_logbooks WHERE placement_id = ? GROUP BY status",
                rs -> { counts.put(rs.getString(1), rs.getLong(2)); }, placementId);
        return counts;
    }
}
