package com.internlink.core.infrastructure.persistence.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.util.*;

@Repository
public class NotificationCampaignAudienceRepository {
    public record Target(UUID userId, String email, String name, String studentCode) {}
    private final JdbcTemplate jdbc;

    public NotificationCampaignAudienceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code department}: khoa của người tạo (lọc theo khoa của sinh viên); null = toàn Trường. */
    public List<Target> audience(UUID term, String audience, UUID department) {
        if ("ALL_STUDENTS".equals(audience))
            return studentAudience(term, department);
        String role = switch (audience) {
            case "MENTORS" -> "COMPANY_MENTOR";
            case "LECTURERS" -> "LECTURER";
            case "COMPANY_REPS" -> "COMPANY_REP";
            case "FACULTY_ADMINS" -> "FACULTY_ADMIN";
            case "ADMINS" -> "ADMIN";
            default -> throw new IllegalArgumentException("invalid audience");
        };
        String placements = "SELECT %s FROM internship_placements p JOIN users s ON s.id=p.student_id WHERE p.term_id=?"
                + (department == null ? "" : " AND s.department_id=?");
        String scope = switch (audience) {
            case "MENTORS" -> "u.id IN (" + placements.formatted("p.mentor_id") + ")";
            case "LECTURERS" -> "u.id IN (" + placements.formatted("p.lecturer_id") + ")";
            case "COMPANY_REPS" -> department == null
                    ? "u.company_id IN (" + placements.formatted("p.company_id")
                        + " UNION SELECT company_id FROM job_positions WHERE term_id=?)"
                    : "u.company_id IN (" + placements.formatted("p.company_id") + ")";
            case "FACULTY_ADMINS" -> department == null
                    ? "EXISTS(SELECT 1 FROM internship_terms WHERE id=?)" : "u.department_id=?";
            default -> "EXISTS(SELECT 1 FROM internship_terms WHERE id=?)";
        };
        List<Object> params = new ArrayList<>(List.of(role));
        switch (audience) {
            case "MENTORS", "LECTURERS" -> {
                params.add(term);
                if (department != null) params.add(department);
            }
            case "COMPANY_REPS" -> {
                params.add(term);
                params.add(department != null ? department : term);
            }
            case "FACULTY_ADMINS" -> params.add(department != null ? department : term);
            default -> params.add(term);
        }
        return jdbc.query(
                "SELECT u.id AS user_id,u.email,u.full_name AS name,NULL AS student_code FROM users u"
                        + " WHERE u.role=? AND u.is_active=true AND " + scope + " ORDER BY u.id",
                (rs, i) -> target(rs), params.toArray());
    }

    private List<Target> studentAudience(UUID term, UUID department) {
        String byDepartment = department == null ? ""
                : " AND r.program_id IN (SELECT id FROM academic_programs WHERE department_id=?)";
        Object[] params = department == null ? new Object[] {term} : new Object[] {term, department};
        return jdbc.query("""
SELECT coalesce(claimed.id,registered.id) AS user_id,coalesce(claimed.email,registered.email,r.official_email) AS email,
    r.full_name AS name,r.student_code
FROM student_rosters r
LEFT JOIN users claimed ON claimed.id=r.claimed_user_id
LEFT JOIN users registered ON r.claimed_user_id IS NULL AND registered.email=r.official_email
WHERE r.term_id=? AND coalesce(claimed.is_active,registered.is_active,true)=true
""" + byDepartment + " ORDER BY r.student_code", (rs, i) -> target(rs), params);
    }

    private Target target(ResultSet rs) throws SQLException {
        return new Target(uuid(rs, "user_id"), rs.getString("email"), rs.getString("name"), rs.getString("student_code"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        String text = rs.getString(column);
        return text == null ? null : UUID.fromString(text);
    }
}
