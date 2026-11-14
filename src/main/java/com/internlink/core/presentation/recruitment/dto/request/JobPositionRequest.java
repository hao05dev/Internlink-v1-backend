package com.internlink.core.presentation.recruitment.dto.request;

import com.internlink.core.shared.enums.ApplicationMethod;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.JobType;
import com.internlink.core.shared.enums.WorkFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
public class JobPositionRequest {

    @NotNull(message = "Doanh nghiệp không được để trống")
    private UUID companyId;

    @Builder.Default
    private JobType jobType = JobType.INTERNSHIP;

    private UUID termId;


    @NotBlank(message = "Tiêu đề vị trí không được để trống")
    @Size(max = 255, message = "Tiêu đề không quá 255 ký tự")
    private String title;

    @NotNull(message = "Hình thức làm việc không được để trống")
    private WorkFormat workFormat;

    @Size(max = 1000)
    private String location;
    private boolean useCompanyLocation;
    @Size(max = 8192) private String mapEmbedUrl;
    @Size(max = 8192) private String mapsUrl;

    private OffsetDateTime applicationDeadline;

    @NotNull(message = "Chỉ tiêu tuyển dụng không được để trống")
    @Min(value = 1, message = "Chỉ tiêu tuyển dụng tối thiểu là 1")
    private Integer vacancies;

    @NotBlank(message = "Mô tả công việc không được để trống")
    private String description;

    @Size(max = 2048)
    private String bannerUrl;

    @Builder.Default private List<Map<String, Object>> attachmentUrls = List.of();

    @Builder.Default private List<String> targetProgramCodes = List.of();

    @Builder.Default private List<String> targetLearningOutcomes = List.of();

    @Builder.Default private List<String> benefits = List.of();

    private BigDecimal stipendAmount;

    @Builder.Default private JobStatus status = JobStatus.DRAFT;

    /**
     * Để trống: VP Trường đăng cho DN chưa có tài khoản đại diện thì mặc định EXTERNAL, còn lại IN_APP.
     * EXTERNAL cần ít nhất một trong link, email hoặc hướng dẫn nộp hồ sơ.
     */
    private ApplicationMethod applicationMethod;

    @Size(max = 2048) private String externalApplyUrl;

    @Email(message = "Email nhận hồ sơ không hợp lệ")
    @Size(max = 255) private String externalApplyEmail;

    @Size(max = 255) private String externalApplySubject;

    @Size(max = 4000) private String externalApplyNote;

    /** Danh sách kỹ năng yêu cầu chi tiết (kèm trọng số và phân loại) */
    @Builder.Default private List<@Valid JobSkillRequest> skills = List.of();

    /** Hỗ trợ truyền nhanh danh sách ID kỹ năng bắt buộc */
    @Builder.Default private List<String> mandatorySkillIds = List.of();

    /** Hỗ trợ truyền nhanh danh sách ID kỹ năng mong muốn/tùy chọn */
    @Builder.Default private List<String> optionalSkillIds = List.of();
}
