package com.internlink.core.presentation.ai_matching.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CvJdAnalysisRequest(
    @NotNull UUID jobId,
    @NotNull UUID documentId,
    @AssertTrue(message = "Cần đồng ý phân tích tài liệu trước khi tiếp tục") boolean consent
) {
}
