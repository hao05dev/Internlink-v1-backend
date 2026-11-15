package com.internlink.core.infrastructure.persistence.jdbc;

import com.internlink.core.application.meeting.SupervisedStudentDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Sinh viên GVHD đang hướng dẫn: theo placement (lecturer_id) hoặc theo danh sách lớp
 * (assigned_lecturer_id, sinh viên đã kích hoạt tài khoản), chỉ ở kỳ chưa đóng.
 */
@Repository
@RequiredArgsConstructor
public class JdbcSupervisedStudentDirectory implements SupervisedStudentDirectory {
    private static final String SQL = """
            SELECT s.user_id, u.full_name, u.email, r.student_code, t.term_name, t.start_date
            FROM (
                SELECT p.student_id AS user_id, p.term_id
                FROM internship_placements p
                WHERE p.lecturer_id = ? AND p.status NOT IN ('TERMINATED', 'TRANSFERRED')
                UNION
                SELECT r.claimed_user_id, r.term_id
                FROM student_rosters r
                WHERE r.assigned_lecturer_id = ? AND r.claimed_user_id IS NOT NULL
            ) s
            JOIN users u ON u.id = s.user_id AND u.is_active = TRUE
            JOIN internship_terms t ON t.id = s.term_id AND t.status NOT IN ('DRAFT', 'CLOSED')
            LEFT JOIN student_rosters r ON r.term_id = s.term_id AND r.claimed_user_id = s.user_id
            ORDER BY t.start_date DESC, u.full_name
            """;

    private final JdbcTemplate jdbc;

    @Override
    public List<SupervisedStudent> forLecturer(UUID lecturerId) {
        List<SupervisedStudent> rows = jdbc.query(
                SQL,
                (rs, i) -> new SupervisedStudent(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("student_code"),
                        rs.getString("term_name")),
                lecturerId,
                lecturerId);
        // Một sinh viên có thể xuất hiện ở nhiều kỳ: giữ kỳ mới nhất (đã sắp theo start_date giảm dần).
        Map<UUID, SupervisedStudent> unique = new LinkedHashMap<>();
        rows.forEach(row -> unique.putIfAbsent(row.userId(), row));
        List<SupervisedStudent> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(SupervisedStudent::fullName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
}
