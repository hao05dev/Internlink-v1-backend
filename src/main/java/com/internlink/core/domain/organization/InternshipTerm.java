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

    /** Số hiệu văn bản kế hoạch TTTT của Trường, vd. "349/CNTT&TT-GV". */
    @Column(name = "plan_document_ref", length = 100)
    private String planDocumentRef;

    /** Thời điểm VP Trường công bố kế hoạch; null = SV/Khoa chưa thấy kế hoạch. */
    @Column(name = "plan_published_at")
    private OffsetDateTime planPublishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_published_by_user_id")
    private com.internlink.core.domain.auth.User planPublishedBy;

    /** Tệp văn bản kế hoạch (media POST_ATTACHMENT) để đính kèm vào bài tin khi công bố. */
    @Column(name = "plan_document_media_id")
    private java.util.UUID planDocumentMediaId;

    /** Bài tin tức công khai tạo lúc công bố; công bố lại thì cập nhật đúng bài này. */
    @Column(name = "plan_news_post_id")
    private java.util.UUID planNewsPostId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "settings", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> settings = Map.of();
}
