package com.internlink.core.domain.organization;

import com.internlink.core.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.Set;

/** Một mốc trong Kế hoạch TTTT của kỳ do Văn phòng Trường ban hành. */
@Entity
@Table(name = "term_milestones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermMilestone extends BaseEntity {

    /** Mốc hệ thống dựa vào để nhắc/kiểm tra; OTHER chỉ để hiển thị. */
    public static final String INTRO_LETTER_PICKUP = "INTRO_LETTER_PICKUP";
    public static final String PROCESS_BRIEFING = "PROCESS_BRIEFING";
    public static final String ACCEPTANCE_FORM_DUE = "ACCEPTANCE_FORM_DUE";
    public static final String FACULTY_REVIEW_DUE = "FACULTY_REVIEW_DUE";
    public static final String LIST_PUBLISH = "LIST_PUBLISH";
    public static final String INTERNSHIP_PERIOD = "INTERNSHIP_PERIOD";
    public static final String REPORT_SUBMISSION = "REPORT_SUBMISSION";
    public static final String GRADING = "GRADING";
    public static final String OTHER = "OTHER";
    public static final Set<String> KINDS = Set.of(INTRO_LETTER_PICKUP, PROCESS_BRIEFING, ACCEPTANCE_FORM_DUE,
        FACULTY_REVIEW_DUE, LIST_PUBLISH, INTERNSHIP_PERIOD, REPORT_SUBMISSION, GRADING, OTHER);

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private InternshipTerm term;

    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    @Column(name = "label", nullable = false, length = 500)
    private String label;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "start_at")
    private OffsetDateTime startAt;

    @Column(name = "end_at")
    private OffsetDateTime endAt;

    /** 8 = học phần 2–3 TC, 12 = học phần 5 TC, null = mọi SV. */
    @Column(name = "required_weeks")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer requiredWeeks;

    /** Khi quá hạn (chỉ áp dụng cho mốc hạn nộp phiếu): cho nộp trễ có đánh dấu, hoặc khóa nộp. */
    public static final String ALLOW_LATE = "ALLOW_LATE";
    public static final String LOCK = "LOCK";

    @Builder.Default
    @Column(name = "late_policy", nullable = false, length = 20)
    private String latePolicy = ALLOW_LATE;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
