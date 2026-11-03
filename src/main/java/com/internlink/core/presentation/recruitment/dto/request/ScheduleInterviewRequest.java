package com.internlink.core.presentation.recruitment.dto.request;

import com.internlink.core.shared.enums.InterviewFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleInterviewRequest {

    private Integer roundNumber;

    @NotNull(message = "Hình thức phỏng vấn không được để trống")
    private InterviewFormat format;

    @NotNull(message = "Thời gian phỏng vấn không được để trống")
    @Future(message = "Thời gian phỏng vấn phải ở tương lai")
    private OffsetDateTime scheduledAt;

    @Size(max = 1000, message = "Liên kết phòng họp không quá 1000 ký tự")
    private String meetingUrl;

    @Size(max = 500, message = "Địa điểm phỏng vấn không quá 500 ký tự")
    private String location;

    @Size(max = 255, message = "Tên người phỏng vấn không quá 255 ký tự")
    private String interviewer;

    @Size(max = 2000, message = "Ghi chú không quá 2000 ký tự")
    private String notes;

    @Builder.Default
    private Boolean sendEmail = true;
}
