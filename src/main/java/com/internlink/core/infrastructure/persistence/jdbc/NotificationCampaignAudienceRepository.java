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

    public List<Target> audience(UUID term, String audience) {
        if ("ALL_STUDENTS".equals(audience))
            return studentAudience(term);
        String role = switch (audience) {
            case "MENTORS" -> "COMPANY_MENTOR";
            case "LECTURERS" -> "LECTURER";
            case "COMPANY_REPS" -> "COMPANY_REP";
            case "FACULTY_ADMINS" -> "FACULTY_ADMIN";
            case "ADMINS" -> "ADMIN";
            default -> throw new IllegalArgumentException("invalid audience");
        };
        String scope = switch (audience) {
            case "MENTORS" -> "u.id IN (SELECT mentor_id FROM internship_placements WHERE term_id=?)";
            case "LECTURERS" -> "u.id IN (SELECT lecturer_id FROM internship_placements WHERE term_id=?)";
            case "COMPANY_REPS" -> "u.company_id IN (SELECT company_id FROM internship_placements WHERE term_id=? UNION SELECT company_id FROM job_positions WHERE term_id=?)";
            case "FACULTY_ADMINS" -> "u.department_id IN (SELECT department_id FROM internship_terms WHERE id=?)";
            default -> "EXISTS(SELECT 1 FROM internship_terms WHERE id=?)";
        };
        Object[] params = audience.equals("COMPANY_REPS")
                ? new Object[] {role, term, term} : new Object[] {role, term};
        return jdbc.query(
                "SELECT u.id AS user_id,u.email,u.full_name AS name,NULL AS student_code FROM users u"
                        + " WHERE u.role=? AND u.is_active=true AND " + scope + " ORDER BY u.id",
                (rs, i) -> target(rs), params);
    }

    private List<Target> studentAudience(UUID term) {
        return jdbc.query("""
SELECT coalesce(claimed.id,registered.id) AS user_id,coalesce(claimed.email,registered.email,r.official_email) AS email,
    r.full_name AS name,r.student_code
FROM student_rosters r
LEFT JOIN users claimed ON claimed.id=r.claimed_user_id
LEFT JOIN users registered ON r.claimed_user_id IS NULL AND registered.email=r.official_email
WHERE r.term_id=? AND coalesce(claimed.is_active,registered.is_active,true)=true
""" + " ORDER BY r.student_code", (rs, i) -> target(rs), term);
    }

    private Target target(ResultSet rs) throws SQLException {
        return new Target(uuid(rs, "user_id"), rs.getString("email"), rs.getString("name"), rs.getString("student_code"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        String text = rs.getString(column);
        return text == null ? null : UUID.fromString(text);
    }
}
