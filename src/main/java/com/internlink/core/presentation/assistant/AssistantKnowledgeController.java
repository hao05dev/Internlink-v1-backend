package com.internlink.core.presentation.assistant;

import com.internlink.core.application.assistant.AssistantInteractionLog;
import com.internlink.core.application.assistant.AssistantKnowledgeAdminService;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository.Detail;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository.Summary;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Kho tri thức của trợ lý CapyLink: quản trị viên hệ thống tải lên, xem trước, bật/tắt tài liệu. */
@RestController
@RequestMapping("/api/v1/admin/assistant-knowledge")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AssistantKnowledgeController {
    private final AssistantKnowledgeAdminService service;
    private final AssistantInteractionLog interactions;

    /** Tỉ lệ 👍/👎 và các câu hỏi chưa trả lời tốt (ẩn danh) để quản trị viên bổ sung tài liệu. */
    @GetMapping("/insights")
    public ApiResponse<Map<String, Object>> insights(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(interactions.insights(Math.max(1, Math.min(days, 365))));
    }

    @GetMapping
    public ApiResponse<List<Summary>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Detail> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> extract(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(value = "title", required = false) String title) throws IOException {
        return ApiResponse.success(service.extract(file.getOriginalFilename(), file.getBytes(), title));
    }

    @PostMapping
    public ApiResponse<Detail> create(@Valid @RequestBody AssistantKnowledgeRequest request) {
        return ApiResponse.success("Đã thêm tài liệu vào kho tri thức.", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Detail> update(@PathVariable UUID id, @Valid @RequestBody AssistantKnowledgeRequest request) {
        return ApiResponse.success("Đã cập nhật tài liệu.", service.update(id, request));
    }

    @PatchMapping("/{id}/active")
    public ApiResponse<Void> setActive(@PathVariable UUID id, @RequestParam boolean value) {
        service.setActive(id, value);
        return ApiResponse.success(value ? "Đã bật tài liệu." : "Đã tắt tài liệu.");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Đã xoá tài liệu.");
    }
}
