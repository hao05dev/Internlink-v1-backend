package com.internlink.core.presentation.ai_matching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMatchScoreResponse {

    private UUID jobId;
    private String jobTitle;
    private String companyName;
    /** IN_APP hoặc EXTERNAL: giúp giao diện đổi nút "Ứng tuyển" cho tin nộp qua kênh của DN. */
    private com.internlink.core.shared.enums.ApplicationMethod applicationMethod;
    private BigDecimal matchScore; // Thang điểm 0 -> 100
    private String scoreLabel; // "Cao", "Vừa", "Thấp", "Chưa đủ dữ liệu"
    private String scoreFormatted; // "85/100"
    private BigDecimal mandatoryScore;
    private BigDecimal optionalScore;
    private BigDecimal semanticScore;
    private String eligibilityStatus; // "ELIGIBLE", "INELIGIBLE"
    private String eligibilityReason;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private Map<String, Object> explanation;
    private String mode;
    private Boolean scoreAvailable;
    private Boolean semanticAvailable;
    private String algorithmVersion;
    private List<String> warnings;
}
