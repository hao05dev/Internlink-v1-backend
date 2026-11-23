package com.internlink.core.domain.evaluation;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.system.Document;
import com.internlink.core.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Một lần AI đọc ảnh phiếu đánh giá giấy; điểm chỉ có hiệu lực sau khi GVHD xác nhận. */
@Entity
@Table(name = "paper_evaluation_scans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaperEvaluationScan extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "placement_id", nullable = false)
    private InternshipPlacement placement;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "document_id", nullable = false)
    private Document document;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "scanned_by_user_id", nullable = false)
    private User scannedBy;
    @Builder.Default
    @Column(name = "form_code", nullable = false, length = 20)
    private String formCode = "M03";
    @Column(name = "model", length = 100)
    private String model;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "reading", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> reading;
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "warnings", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> warnings = new ArrayList<>();
    @Column(name = "suggested_score", precision = 4, scale = 1)
    private BigDecimal suggestedScore;
    /** Bản GVHD chốt: criteria, comments, ctdtOptions, ctdtSuggestion, componentCode. */
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "confirmed", columnDefinition = "jsonb")
    private Map<String, Object> confirmed;
    @Column(name = "confirmed_score", precision = 4, scale = 1)
    private BigDecimal confirmedScore;
    @Column(name = "edited_fields")
    private Integer editedFields;
    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
}
