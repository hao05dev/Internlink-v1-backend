package com.internlink.core.application.assistant;

import com.internlink.core.infrastructure.persistence.jdbc.AssistantInteractionRepository;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantInteractionRepository.Entry;
import com.internlink.core.shared.exception.BadRequestException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantInteractionLogTest {
    @Mock AssistantInteractionRepository repository;
    @InjectMocks AssistantInteractionLog log;

    @Test
    void anonymizesIdentifiersBeforeStoring() {
        String masked = AssistantInteractionLog.anonymize("Em là B2110940, mail b2110940@student.ctu.edu.vn, sđt 0912 345 678");
        assertThat(masked).doesNotContain("B2110940", "b2110940@", "0912").contains("[MSSV]", "[email]", "[SĐT]");
    }

    @Test
    void recordsSummaryWithoutAskerIdentity() {
        UUID id = log.record("Điều kiện thực tập?", "STUDENT",
                Map.of("status", "ANSWERED", "intents", List.of("regulation"), "citations", List.of(1, 2), "mode", "llm"), 420);

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(repository).insert(captor.capture());
        Entry entry = captor.getValue();
        assertThat(id).isEqualTo(entry.id());
        assertThat(entry.intents()).isEqualTo("regulation");
        assertThat(entry.citationCount()).isEqualTo(2);
        assertThat(entry.latencyMs()).isEqualTo(420);
    }

    @Test
    void loggingFailureDoesNotBreakTheAnswer() {
        doThrow(new RuntimeException("db down")).when(repository).insert(any());
        assertThat(log.record("Hỏi", "LECTURER", Map.of("status", "ANSWERED"), 10)).isNull();
    }

    @Test
    void rejectsSecondRating() {
        UUID id = UUID.randomUUID();
        when(repository.rate(eq(id), eq(false), isNull())).thenReturn(false);
        assertThatThrownBy(() -> log.rate(id, false, "  ")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void masksIdentifiersInFeedbackNote() {
        UUID id = UUID.randomUUID();
        when(repository.rate(any(), anyBoolean(), any())).thenReturn(true);
        log.rate(id, false, "Sai rồi, hỏi lại B2110940");
        verify(repository).rate(id, false, "Sai rồi, hỏi lại [MSSV]");
    }
}
