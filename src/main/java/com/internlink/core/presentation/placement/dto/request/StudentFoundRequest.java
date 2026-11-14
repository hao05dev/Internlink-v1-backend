package com.internlink.core.presentation.placement.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Nội dung Form Phiếu tiếp nhận. {@code agreementId} chỉ có khi SV trúng tuyển qua hệ thống
 * (thỏa thuận 3 bên đã duyệt); để trống với nơi thực tập tự tìm.
 * {@code sourceJobId} (tùy chọn, chỉ với nơi tự tìm): tin EXTERNAL mà SV đã ứng tuyển ngoài hệ thống.
 */
public record StudentFoundRequest(@NotNull UUID termId, @NotBlank String hostName,
    @NotBlank String hostAddress, @NotBlank String contactName, @NotBlank @Email String contactEmail,
    @NotBlank String workDescription, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
    UUID agreementId, UUID sourceJobId) {

    /** Giữ tương thích cho chỗ gọi cũ không có tin nguồn. */
    public StudentFoundRequest(UUID termId, String hostName, String hostAddress, String contactName,
        String contactEmail, String workDescription, LocalDate startDate, LocalDate endDate, UUID agreementId) {
        this(termId, hostName, hostAddress, contactName, contactEmail, workDescription, startDate, endDate,
            agreementId, null);
    }
}
