package com.internlink.core.presentation.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Tài liệu Khoa đưa vào kho tri thức. Khi sửa, {@code content} để trống nghĩa là giữ nguyên toàn văn cũ.
 */
public record AssistantKnowledgeRequest(
        @NotBlank @Size(max = 300) String title,
        @Size(max = 50) String category,
        @Size(max = 500) @Pattern(regexp = "^$|^(https?://|/).*$", message = "Liên kết phải bắt đầu bằng http(s):// hoặc /") String sourceUrl,
        @Size(max = 100_000) String content,
        @NotBlank @Pattern(regexp = "PUBLIC|INTERNAL") String visibility,
        LocalDate issuedOn,
        Boolean active) {}
