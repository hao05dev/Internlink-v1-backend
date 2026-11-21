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
 * Chỉ đọc tên/email của GVHD và mentor được phân công cho chính sinh viên đó (giống thẻ trên dashboard);
 * không đọc dữ liệu của sinh viên khác.
 */
@Repository
@RequiredArgsConstructor
public class StudentInternshipSnapshotRepository {
    private final JdbcTemplate jdbc;

    public record Profile(String fullName, String email, String studentCode, String programName) {}

    public record Enrollment(String termName, String studentCode, boolean letterReceived,
                             String lecturerName, String lecturerEmail) {}

    public record Placement(UUID id, String companyName, String status, LocalDate startDate, LocalDate endDate,
                            String lecturerName, String lecturerEmail, String mentorName, String mentorEmail) {}

    public Optional<Profile> profile(UUID userId) {
        return jdbc.query("SELECT u.full_name, u.email, sp.student_code, ap.name FROM users u"
                        + " LEFT JOIN student_profiles sp ON sp.user_id = u.id"
                        + " LEFT JOIN academic_programs ap ON ap.id = sp.program_id WHERE u.id = ?",
                (rs, i) -> new Profile(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), userId)
                .stream().findFirst();
    }

    public Optional<Enrollment> latestEnrollment(UUID userId) {
        // GVHD lấy theo tài khoản được phân công; chưa liên kết tài khoản thì dùng tên/email trong danh sách Khoa gửi.
        return jdbc.query("SELECT t.term_name, r.student_code, r.letter_received,"
                        + " COALESCE(l.full_name, r.lecturer_name), COALESCE(l.email, r.lecturer_email)"
                        + " FROM student_rosters r JOIN internship_terms t ON t.id = r.term_id"
                        + " LEFT JOIN users l ON l.id = r.assigned_lecturer_id WHERE r.claimed_user_id = ?"
                        + " ORDER BY t.start_date DESC NULLS LAST LIMIT 1",
                (rs, i) -> new Enrollment(rs.getString(1), rs.getString(2), rs.getBoolean(3),
                        rs.getString(4), rs.getString(5)), userId)
                .stream().findFirst();
    }

    public Map<String, Long> applicationCounts(UUID userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) FROM job_applications WHERE student_id = ? GROUP BY status",
                rs -> { counts.put(rs.getString(1), rs.getLong(2)); }, userId);
        return counts;
    }

    public Optional<Placement> latestPlacement(UUID userId) {
        // Nơi tự tìm (STUDENT_FOUND) không có company_id/mentor_id: lấy tên đơn vị từ đơn tự tìm.
        List<Placement> rows = jdbc.query("SELECT p.id, COALESCE(c.company_name, sfa.host_name), p.status,"
                        + " p.start_date, p.end_date, l.full_name, l.email, m.full_name, m.email"
                        + " FROM internship_placements p LEFT JOIN companies c ON c.id = p.company_id"
                        + " LEFT JOIN student_found_applications sfa ON sfa.id = p.student_found_application_id"
                        + " LEFT JOIN users l ON l.id = p.lecturer_id LEFT JOIN users m ON m.id = p.mentor_id"
                        + " WHERE p.student_id = ? ORDER BY p.start_date DESC NULLS LAST, p.created_at DESC LIMIT 1",
                (rs, i) -> new Placement(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9)), userId);
        return rows.stream().findFirst();
    }

    public Map<String, Long> logbookCounts(UUID placementId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) FROM weekly_logbooks WHERE placement_id = ? GROUP BY status",
                rs -> { counts.put(rs.getString(1), rs.getLong(2)); }, placementId);
        return counts;
    }
}
