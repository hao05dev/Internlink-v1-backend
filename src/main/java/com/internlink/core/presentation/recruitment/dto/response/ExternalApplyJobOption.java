package com.internlink.core.presentation.recruitment.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Tin ứng tuyển ngoài SV đã mở kênh của DN – lựa chọn "tin nguồn" cho Phiếu tiếp nhận SELF_FOUND. */
public record ExternalApplyJobOption(
        UUID jobId,
        String jobTitle,
        String companyName,
        String hostAddress,
        String externalApplyEmail,
        OffsetDateTime lastClickedAt) {}
