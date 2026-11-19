package com.internlink.core.application.assistant;

import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.assistant.AssistantChatRequest;
import com.internlink.core.shared.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ghép ngữ cảnh được phép cho từng người hỏi rồi chuyển sang AI service.
 * Khách chỉ thấy dữ liệu công khai; sinh viên có thêm tiến độ của chính mình. Không gửi dữ liệu người khác.
 */
@Service
@RequiredArgsConstructor
public class AssistantChatService {
    static final String UNAVAILABLE = "CapyLink đang tạm nghỉ một chút. Bạn thử lại sau ít phút, hoặc xem mục "
            + "Cẩm nang thực tập và trang Việc làm trong lúc chờ nhé.";

    private final AiServiceClient ai;
    private final AssistantKnowledgeProvider knowledge;
    private final AssistantStudentStatusService studentStatus;
    private final AssistantInteractionLog interactions;

    public Map<String, Object> chat(AssistantChatRequest request, CustomUserDetail user) {
        String audience = audienceOf(user);
        Map<String, Object> body = new HashMap<>();
        body.put("message", request.message().strip());
        body.put("history", request.history() == null ? List.of() : request.history().stream()
                .map(turn -> Map.of("role", turn.role(), "content", turn.content())).toList());
        body.put("audience", audience);
        body.put("knowledge", knowledge.knowledge(user != null));
        body.put("jobs", knowledge.openJobs());
        Map<String, Object> trends = knowledge.trendSnapshot();
        if (trends != null) body.put("trends", trends);
        if (user != null && user.getRole() == UserRole.STUDENT) {
            AssistantStudentStatusService.Snapshot snapshot = studentStatus.forStudent(user.getId());
            body.put("me", snapshot.items());
            body.put("own_identifiers", snapshot.ownIdentifiers());
        }
        body.put("allow_cloud", !Boolean.FALSE.equals(request.allowCloud()));
        long started = System.currentTimeMillis();
        Map<String, Object> response = ai.assistantChat(body);
        if ("FAILED".equals(response.get("status"))) return unavailable();
        Map<String, Object> result = new HashMap<>(response);
        UUID logId = interactions.record(request.message(), audience, response, System.currentTimeMillis() - started);
        if (logId != null) result.put("log_id", logId.toString());
        return result;
    }

    static String audienceOf(CustomUserDetail user) {
        if (user == null) return "GUEST";
        return switch (user.getRole()) {
            case STUDENT -> "STUDENT";
            case LECTURER -> "LECTURER";
            case COMPANY_MENTOR -> "MENTOR";
            case COMPANY_REP -> "COMPANY";
            default -> "STAFF";
        };
    }

    private static Map<String, Object> unavailable() {
        Map<String, Object> response = new HashMap<>();
        response.put("answer", UNAVAILABLE);
        response.put("status", "UNAVAILABLE");
        response.put("intents", List.of());
        response.put("citations", List.of());
        response.put("jobs", List.of());
        response.put("relaxed_filters", List.of());
        response.put("trend_highlights", List.of());
        response.put("suggestions", List.of());
        response.put("notices", List.of());
        response.put("mode", "extractive");
        return response;
    }
}
