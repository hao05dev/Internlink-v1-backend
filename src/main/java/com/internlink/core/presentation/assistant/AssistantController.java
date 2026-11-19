package com.internlink.core.presentation.assistant;

import com.internlink.core.application.assistant.AssistantChatService;
import com.internlink.core.application.assistant.AssistantInteractionLog;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Trợ lý CapyLink: chỉ dành cho sinh viên và giảng viên đã đăng nhập; trả lời theo vai trò người hỏi. */
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {
    private final AssistantChatService assistant;
    private final AssistantInteractionLog interactions;

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('STUDENT', 'LECTURER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> chat(
            @Valid @RequestBody AssistantChatRequest request,
            @AuthenticationPrincipal CustomUserDetail user) {
        return ResponseEntity.ok(ApiResponse.success(assistant.chat(request, user)));
    }

    /** Chấm 👍/👎 theo log_id trả về cùng câu trả lời; nhật ký không gắn với người hỏi. */
    @PostMapping("/chat/{logId}/feedback")
    @PreAuthorize("hasAnyRole('STUDENT', 'LECTURER')")
    public ResponseEntity<ApiResponse<Void>> feedback(@PathVariable UUID logId,
                                                      @Valid @RequestBody AssistantFeedbackRequest request) {
        interactions.rate(logId, request.helpful(), request.note());
        return ResponseEntity.ok(ApiResponse.success("Cảm ơn bạn đã góp ý cho CapyLink."));
    }
}
