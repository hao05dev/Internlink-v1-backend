package com.internlink.core.presentation.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Một câu hỏi gửi CapyLink; lịch sử do trình duyệt giữ (không lưu ở máy chủ). */
public record AssistantChatRequest(
        @NotBlank @Size(max = 1000) String message,
        @Valid @Size(max = 8) List<Turn> history,
        Boolean allowCloud
) {
    public record Turn(@Pattern(regexp = "user|assistant") String role, @NotBlank @Size(max = 2000) String content) {}
}
