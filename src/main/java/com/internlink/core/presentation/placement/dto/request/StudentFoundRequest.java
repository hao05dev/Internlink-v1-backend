package com.internlink.core.presentation.placement.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Nội dung Form Phiếu tiếp nhận. {@code agreementId} chỉ có khi SV trúng tuyển qua hệ thống
 * (thỏa thuận 3 bên đã duyệt); để trống với nơi thực tập tự tìm.
 * {@code details}: các field bổ sung theo Google Form của Trường.
 * {@code sourceJobId} (tùy chọn, chỉ với nơi tự tìm): tin EXTERNAL mà SV đã ứng tuyển ngoài hệ thống.
 */
public record StudentFoundRequest(@NotNull UUID termId, @NotBlank String hostName,
    @NotBlank String hostAddress, @NotBlank String contactName, @NotBlank @Email String contactEmail,
    @NotBlank String workDescription, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
    UUID agreementId, UUID sourceJobId, @Valid AcceptanceFormDetails details) {

    /** Giữ tương thích cho chỗ gọi chưa gửi các field bổ sung. */
    public StudentFoundRequest(UUID termId, String hostName, String hostAddress, String contactName,
        String contactEmail, String workDescription, LocalDate startDate, LocalDate endDate, UUID agreementId,
        UUID sourceJobId) {
        this(termId, hostName, hostAddress, contactName, contactEmail, workDescription, startDate, endDate,
            agreementId, sourceJobId, null);
    }

    /** Giữ tương thích cho chỗ gọi cũ không có tin nguồn. */
    public StudentFoundRequest(UUID termId, String hostName, String hostAddress, String contactName,
        String contactEmail, String workDescription, LocalDate startDate, LocalDate endDate, UUID agreementId) {
        this(termId, hostName, hostAddress, contactName, contactEmail, workDescription, startDate, endDate,
            agreementId, null);
    }
}
