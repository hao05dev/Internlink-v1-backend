package com.internlink.core.presentation.assistant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Đánh giá 👍/👎 cho một câu trả lời; ghi chú tuỳ chọn (sẽ được che định danh trước khi lưu). */
public record AssistantFeedbackRequest(
        @NotNull Boolean helpful,
        @Size(max = 500) String note
) {}
