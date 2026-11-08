package com.internlink.core.presentation.ai_matching.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** includeGithub: opt-in, lets the AI service read public repositories linked in the CV. */
public record CvJdAnalysisRequest(
    @NotNull UUID jobId,
    @NotNull UUID documentId,
    @AssertTrue(message = "Cần đồng ý phân tích tài liệu trước khi tiếp tục") boolean consent,
    boolean includeGithub
) {
    public CvJdAnalysisRequest(UUID jobId, UUID documentId, boolean consent) {
        this(jobId, documentId, consent, false);
    }
}
