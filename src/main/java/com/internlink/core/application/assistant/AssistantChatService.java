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
 * Hai lớp dữ liệu tách bạch:
 * - Công khai (mọi người): văn bản, cẩm nang, tin tuyển, xu hướng, lịch/quy định các kỳ thực tập đã công bố.
 * - Cá nhân (chỉ chính người hỏi): danh tính, tiến độ, GVHD/mentor được phân công. Không bao giờ gửi dữ liệu người khác.
 */
@Service
@RequiredArgsConstructor
public class AssistantChatService {
    static final String UNAVAILABLE = "CapyLink đang tạm nghỉ một chút. Bạn thử lại sau ít phút, hoặc xem mục "
            + "Cẩm nang thực tập và trang Việc làm trong lúc chờ nhé.";

    private final AiServiceClient ai;
    private final AssistantKnowledgeProvider knowledge;
    private final AssistantTermInfoProvider termInfo;
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
        // Dữ liệu công khai do VP Trường công bố (lịch, quy định chung của kỳ) — ai cũng xem được.
        body.put("terms", termInfo.publishedTerms());
        Map<String, Object> trends = knowledge.trendSnapshot();
        if (trends != null) body.put("trends", trends);
        if (user != null && user.getRole() == UserRole.STUDENT) {
            AssistantStudentStatusService.Snapshot snapshot = studentStatus.forStudent(user.getId());
            body.put("me", snapshot.items());
            body.put("own_identifiers", snapshot.ownIdentifiers());
        } else if (user != null) {
            // Vai trò khác chỉ cần biết "tôi là ai": lấy từ phiên đăng nhập, không truy vấn thêm.
            body.put("me", List.of(Map.of("key", "identity", "label", "Bạn là", "value", identityOf(user))));
            if (user.getEmail() != null) body.put("own_identifiers", List.of(user.getEmail()));
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

    static String identityOf(CustomUserDetail user) {
        String role = switch (user.getRole()) {
            case STUDENT -> "sinh viên";
            case LECTURER -> "giảng viên";
            case COMPANY_MENTOR -> "mentor doanh nghiệp";
            case COMPANY_REP -> "đại diện doanh nghiệp";
            default -> "cán bộ quản lý thực tập";
        };
        String name = user.getFullName() == null || user.getFullName().isBlank() ? user.getEmail() : user.getFullName();
        return name + " — tài khoản " + role;
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
