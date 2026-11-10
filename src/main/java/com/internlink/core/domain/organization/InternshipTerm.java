package com.internlink.core.domain.organization;

import com.internlink.core.shared.domain.BaseEntity;
import com.internlink.core.shared.enums.TermStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;



/** Kỳ thực tập cấp Trường CNTT-TT (Văn phòng Trường tạo); Khoa quản lý phần SV thuộc khoa mình. */
@Entity
@Table(name = "internship_terms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternshipTerm extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "term_name", nullable = false, length = 150)
    private String termName;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "semester", nullable = false, length = 20)
    private String semester;

    @Column(name = "registration_open_at", nullable = false)
    private OffsetDateTime registrationOpenAt;

    @Column(name = "registration_close_at", nullable = false)
    private OffsetDateTime registrationCloseAt;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "application_deadline", nullable = false)
    private OffsetDateTime applicationDeadline;

    @Column(name = "evaluation_deadline", nullable = false)
    private OffsetDateTime evaluationDeadline;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private TermStatus status = TermStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "settings", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> settings = Map.of();
}
