package com.internlink.core.domain.recruitment;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.shared.domain.BaseEntity;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.JobType;
import com.internlink.core.shared.enums.WorkFormat;

import jakarta.persistence.*;

import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "job_positions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPosition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = true)
    private InternshipTerm term;


    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "job_type", nullable = false, length = 20)
    private JobType jobType = JobType.INTERNSHIP;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_format", nullable = false, length = 30)
    private WorkFormat workFormat;

    @Column(name = "location", nullable = false, length = 1000)
    private String location;
    @Column(name = "use_company_location", nullable = false)
    private boolean useCompanyLocation;
    @Column(name = "map_embed_url", columnDefinition = "text")
    private String mapEmbedUrl;
    @Column(name = "maps_url", columnDefinition = "text")
    private String mapsUrl;

    @Column(name = "application_deadline")
    private OffsetDateTime applicationDeadline;

    @Column(name = "closed_early_at")
    private OffsetDateTime closedEarlyAt;

    @Builder.Default
    @Column(name = "vacancies", nullable = false)
    private Integer vacancies = 1;

    @Column(name = "banner_url", columnDefinition = "text")
    private String bannerUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "attachment_urls", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> attachmentUrls = List.of();

    @Column(name = "description", columnDefinition = "text", nullable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "target_program_codes", columnDefinition = "jsonb", nullable = false)
    private List<String> targetProgramCodes = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "target_learning_outcomes", columnDefinition = "jsonb", nullable = false)
    private List<String> targetLearningOutcomes = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "benefits", columnDefinition = "jsonb", nullable = false)
    private List<String> benefits = List.of();

    @Column(name = "stipend_amount", precision = 12, scale = 2)
    private BigDecimal stipendAmount;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private JobStatus status = JobStatus.DRAFT;

    @Column(name = "faculty_feedback", columnDefinition = "text")
    private String facultyFeedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    /** User tạo vị trí thực tập (COMPANY_REP). Dùng để kiểm tra quyền ký thỏa thuận. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;
}
