package com.internlink.core.presentation.placement.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningAgreementRequest {

    @NotNull(message = "Offer không được để trống")
    private UUID offerId;

    /** Không còn bắt buộc: khoa của thỏa thuận luôn là khoa của sinh viên. */
    private UUID departmentId;

    @NotNull(message = "Số tín chỉ thực tập không được để trống")
    @Min(value = 1, message = "Số tín chỉ tối thiểu là 1")
    private Integer targetCredits;

    @NotBlank(message = "Mục tiêu học tập không được để trống")
    private String learningObjectives;
}