package com.internlink.core.presentation.organization.dto.request;

import com.internlink.core.shared.enums.TermStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternshipTermRequest {


    @NotBlank(message = "Mã kỳ thực tập không được để trống")
    @Size(max = 50, message = "Mã kỳ không quá 50 ký tự")
    private String code;

    @NotBlank(message = "Tên kỳ thực tập không được để trống")
    @Size(max = 150, message = "Tên kỳ không quá 150 ký tự")
    private String termName;

    @NotBlank(message = "Năm học không được để trống")
    private String academicYear;

    @NotBlank(message = "Học kỳ không được để trống")
    private String semester;

    @NotNull(message = "Thời gian mở đăng ký không được để trống")
    private OffsetDateTime registrationOpenAt;

    @NotNull(message = "Thời gian đóng đăng ký không được để trống")
    private OffsetDateTime registrationCloseAt;

    @NotNull(message = "Ngày bắt đầu thực tập không được để trống")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc thực tập không được để trống")
    private LocalDate endDate;

    @NotNull(message = "Hạn nộp đơn ứng tuyển không được để trống")
    private OffsetDateTime applicationDeadline;

    @NotNull(message = "Hạn đánh giá cuối kỳ không được để trống")
    private OffsetDateTime evaluationDeadline;

    @Builder.Default
    private TermStatus status = TermStatus.DRAFT;

    @Builder.Default
    private Map<String, Object> settings = Map.of();
}