package com.internlink.core.presentation.recruitment.dto.response;

import com.internlink.core.shared.enums.ApplicationMethod;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.JobType;
import com.internlink.core.shared.enums.WorkFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPositionResponse {

    private UUID id;
    private UUID companyId;
    private String companyName;
    private com.internlink.core.presentation.company.dto.response.CompanySummaryResponse company;
    private boolean useCompanyLocation;
    private String mapEmbedUrl;
    private String mapsUrl;
    private UUID termId;
    private String termName;
    private String termStatus;
    private JobType jobType;
    private String title;
    private WorkFormat workFormat;
    private String location;
    private Integer vacancies;
    private String description;
    private String bannerUrl;
    private List<Map<String, Object>> attachmentUrls;
    private List<String> targetProgramCodes;
    private List<String> targetLearningOutcomes;
    private List<String> benefits;
    private BigDecimal stipendAmount;
    private JobStatus status;
    private String facultyFeedback;
    private UUID approvedByUserId;
    private OffsetDateTime approvedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime applicationDeadline;
    private OffsetDateTime closedEarlyAt;
    private Boolean isExpired;
    private Boolean isAcceptingApplications;

    /** IN_APP hoặc EXTERNAL; với EXTERNAL, SV nộp qua các kênh externalApply* dưới đây. */
    private ApplicationMethod applicationMethod;
    private String externalApplyUrl;
    private String externalApplyEmail;
    private String externalApplySubject;
    private String externalApplyNote;

    /** Số hồ sơ đang được xét (không tính hồ sơ đã rút); chỉ điền ở danh sách công khai. */
    private Long applicantCount;

    /** Danh sách kỹ năng yêu cầu chi tiết */
    @Builder.Default private List<JobSkillResponse> skills = List.of();

    /** Danh sách mã kỹ năng bắt buộc */
    @Builder.Default private List<String> mandatorySkillIds = List.of();

    /** Danh sách mã kỹ năng mong muốn */
    @Builder.Default private List<String> optionalSkillIds = List.of();
}
