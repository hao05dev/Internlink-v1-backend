package com.internlink.core.presentation.placement.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Nội dung Form Phiếu tiếp nhận. {@code agreementId} chỉ có khi SV trúng tuyển qua hệ thống
 * (thỏa thuận 3 bên đã duyệt); để trống với nơi thực tập tự tìm.
 */
public record StudentFoundRequest(@NotNull UUID termId, @NotBlank String hostName,
    @NotBlank String hostAddress, @NotBlank String contactName, @NotBlank @Email String contactEmail,
    @NotBlank String workDescription, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
    UUID agreementId) {}
