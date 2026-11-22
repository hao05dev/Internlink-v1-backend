package com.internlink.core.presentation.placement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** GVHD duyệt một file hồ sơ: APPROVED hoặc REVISION_REQUIRED (bắt buộc có lý do). */
public record DossierReviewRequest(
    @NotBlank String decision,
    @Size(max = 2000) String note
) {
}
