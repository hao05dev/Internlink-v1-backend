package com.internlink.core.application.assistant;

import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository.Detail;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository.Draft;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository.Summary;
import com.internlink.core.presentation.assistant.AssistantKnowledgeRequest;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssistantKnowledgeAdminServiceTest {
    private final AssistantDocumentRepository documents = mock(AssistantDocumentRepository.class);
    private final AssistantKnowledgeProvider knowledge = mock(AssistantKnowledgeProvider.class);
    private final AiServiceClient ai = mock(AiServiceClient.class);
    private final AssistantKnowledgeAdminService service = new AssistantKnowledgeAdminService(documents, knowledge, ai);
    private static final String LONG = "Điều 1. Phạm vi điều chỉnh: văn bản này quy định về học phần thực tập của sinh viên.";

    private static AssistantKnowledgeRequest request(String content) {
        return new AssistantKnowledgeRequest(" Sổ tay SV ", null, " ", content, "INTERNAL", null, null);
    }

    private static Detail detail(UUID id) {
        return new Detail(new Summary(id, "Sổ tay SV", "REGULATIONS_GUIDELINES", null, "INTERNAL", null, true, 80, null), LONG);
    }

    @Test
    void createStoresTrimmedDraftAndRefreshesKnowledgeCache() {
        UUID id = UUID.randomUUID();
        when(documents.insert(any())).thenReturn(id);
        when(documents.findById(id)).thenReturn(Optional.of(detail(id)));

        service.create(request("  " + LONG + "  "));

        ArgumentCaptor<Draft> draft = ArgumentCaptor.forClass(Draft.class);
        verify(documents).insert(draft.capture());
        assertThat(draft.getValue().title()).isEqualTo("Sổ tay SV");
        assertThat(draft.getValue().content()).isEqualTo(LONG);
        assertThat(draft.getValue().sourceUrl()).isNull();
        assertThat(draft.getValue().category()).isEqualTo("REGULATIONS_GUIDELINES");
        assertThat(draft.getValue().active()).isTrue();
        verify(knowledge).invalidate();
    }

    @Test
    void rejectsTooShortContent() {
        assertThatThrownBy(() -> service.create(request("ngắn"))).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(knowledge);
    }

    @Test
    void updateWithBlankContentKeepsOldText() {
        UUID id = UUID.randomUUID();
        when(documents.update(eq(id), any())).thenReturn(true);
        when(documents.findById(id)).thenReturn(Optional.of(detail(id)));

        service.update(id, request(""));

        ArgumentCaptor<Draft> draft = ArgumentCaptor.forClass(Draft.class);
        verify(documents).update(eq(id), draft.capture());
        assertThat(draft.getValue().content()).isNull();
        verify(knowledge).invalidate();
    }

    @Test
    void unknownDocumentIsNotFound() {
        UUID id = UUID.randomUUID();
        when(documents.setActive(id, false)).thenReturn(false);
        assertThatThrownBy(() -> service.setActive(id, false)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void extractRejectsUnsupportedTypesBeforeCallingAi() {
        assertThatThrownBy(() -> service.extract("virus.exe", new byte[]{1}, null)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(ai);
    }

    @Test
    void extractExplainsScannedPdf() {
        when(ai.extractKnowledge(eq("qd.pdf"), anyString(), any())).thenReturn(Map.of("ok", false, "error", "ocr_required"));
        assertThatThrownBy(() -> service.extract("qd.pdf", new byte[]{1, 2}, "QĐ"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("bản scan");
    }
}
