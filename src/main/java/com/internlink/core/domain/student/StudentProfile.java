package com.internlink.core.domain.student;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.AcademicProgram;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;



@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private AcademicProgram program;

    @Column(name = "student_code", nullable = false, unique = true, length = 50)
    private String studentCode;

    @Column(name = "gpa", precision = 3, scale = 2)
    private BigDecimal gpa;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "certificates", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> certificates = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "passed_courses", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> passedCourses = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "preferences", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> preferences = Map.of();

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
