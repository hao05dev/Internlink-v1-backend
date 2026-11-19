package com.internlink.core.application.assistant;

import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.assistant.AssistantChatRequest;
import com.internlink.core.shared.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantChatServiceTest {
    @Mock AiServiceClient ai;
    @Mock AssistantKnowledgeProvider knowledge;
    @Mock AssistantStudentStatusService studentStatus;
    @Mock AssistantInteractionLog interactions;
    @InjectMocks AssistantChatService service;

    private static CustomUserDetail user(UserRole role) {
        return CustomUserDetail.builder().id(UUID.randomUUID()).email("x@ctu.edu.vn").role(role).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentBody() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ai).assistantChat(captor.capture());
        return captor.getValue();
    }

    @Test
    void guestReceivesOnlyPublicContextAndNoPersonalStatus() {
        when(ai.assistantChat(any())).thenReturn(Map.of("answer", "ok", "status", "ANSWERED"));

        service.chat(new AssistantChatRequest("  Quy trình thực tập?  ", null, null), null);

        Map<String, Object> body = sentBody();
        assertThat(body.get("audience")).isEqualTo("GUEST");
        assertThat(body.get("message")).isEqualTo("Quy trình thực tập?");
        assertThat(body).doesNotContainKeys("me", "own_identifiers");
        verify(knowledge).knowledge(false);
        verifyNoInteractions(studentStatus);
    }

    @Test
    void studentGetsOwnStatusOnly() {
        CustomUserDetail student = user(UserRole.STUDENT);
        when(studentStatus.forStudent(student.getId())).thenReturn(new AssistantStudentStatusService.Snapshot(
                List.of(Map.of("label", "Đợt thực tập", "value", "HK1")), List.of("B2100001")));
        when(ai.assistantChat(any())).thenReturn(Map.of("answer", "ok"));

        service.chat(new AssistantChatRequest("Mình đang ở bước nào?", List.of(), false), student);

        Map<String, Object> body = sentBody();
        assertThat(body.get("audience")).isEqualTo("STUDENT");
        assertThat(body.get("own_identifiers")).isEqualTo(List.of("B2100001"));
        assertThat(body.get("allow_cloud")).isEqualTo(false);
        verify(studentStatus).forStudent(student.getId());
    }

    @Test
    void lecturerNeverTriggersStudentLookup() {
        when(ai.assistantChat(any())).thenReturn(Map.of("answer", "ok"));
        service.chat(new AssistantChatRequest("Xu hướng kỹ năng?", null, null), user(UserRole.LECTURER));
        assertThat(sentBody().get("audience")).isEqualTo("LECTURER");
        verifyNoInteractions(studentStatus);
    }

    @Test
    void aiOutageReturnsFriendlyFallback() {
        when(ai.assistantChat(any())).thenReturn(Map.of("status", "FAILED", "error_code", "ai_unavailable"));
        Map<String, Object> response = service.chat(new AssistantChatRequest("Xin chào", null, null), null);
        assertThat(response.get("status")).isEqualTo("UNAVAILABLE");
        assertThat(response.get("answer")).isEqualTo(AssistantChatService.UNAVAILABLE);
    }

    @Test
    void returnsLogIdSoTheUserCanRateTheAnswer() {
        UUID logId = UUID.randomUUID();
        when(ai.assistantChat(any())).thenReturn(Map.of("answer", "ok", "status", "ANSWERED"));
        when(interactions.record(eq("Quy trình?"), eq("LECTURER"), any(), anyLong())).thenReturn(logId);

        Map<String, Object> result = service.chat(new AssistantChatRequest("Quy trình?", null, null), user(UserRole.LECTURER));

        assertThat(result.get("log_id")).isEqualTo(logId.toString());
    }
}
