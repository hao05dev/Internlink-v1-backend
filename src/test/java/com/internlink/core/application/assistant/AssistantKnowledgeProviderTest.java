package com.internlink.core.application.assistant;

import com.internlink.core.application.recruitment.JobPositionService;
import com.internlink.core.application.system.CareerGuidePostService;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository;
import com.internlink.core.infrastructure.persistence.jdbc.AssistantDocumentRepository.Document;
import com.internlink.core.infrastructure.persistence.jdbc.JobSkillTrendRepository;
import com.internlink.core.presentation.recruitment.dto.response.JobPositionResponse;
import com.internlink.core.presentation.recruitment.dto.response.JobSkillResponse;
import com.internlink.core.presentation.system.dto.response.PostResponse;
import com.internlink.core.shared.enums.JobType;
import com.internlink.core.shared.enums.PostCategory;
import com.internlink.core.shared.enums.WorkFormat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AssistantKnowledgeProviderTest {
    private final CareerGuidePostService posts = mock(CareerGuidePostService.class);
    private final JobPositionService jobs = mock(JobPositionService.class);
    private final JobSkillTrendRepository trends = mock(JobSkillTrendRepository.class);
    private final AssistantDocumentRepository documents = mock(AssistantDocumentRepository.class);
    private final AssistantKnowledgeProvider provider = new AssistantKnowledgeProvider(posts, jobs, trends, documents);

    private static PostResponse post(String visibility) {
        return PostResponse.builder().id(UUID.randomUUID()).slug("quy-trinh").title("Quy trình thực tập")
                .category(PostCategory.REGULATIONS_GUIDELINES).summary("Tóm tắt").content("<p>Nội dung</p>")
                .visibility(visibility).build();
    }

    @Test
    void guestsOnlySeePublicPostsAndResultsAreCached() {
        when(posts.getPublishedPosts(null, null)).thenReturn(List.of(post("PUBLIC"), post("INTERNAL")));

        List<Map<String, Object>> guest = provider.knowledge(false);
        provider.knowledge(false);

        assertThat(guest).hasSize(1);
        assertThat(guest.getFirst().get("url")).isEqualTo("/career-guide/quy-trinh");
        assertThat((String) guest.getFirst().get("content")).startsWith("Tóm tắt");
        assertThat(provider.knowledge(true)).hasSize(2);
        verify(posts, times(2)).getPublishedPosts(null, null);
    }

    @Test
    void officialDocumentsComeFirstAndKeepTheirSourceUrl() {
        Document rule = new Document(UUID.randomUUID(), "QĐ 3266 Quy định học vụ", "REGULATIONS_GUIDELINES",
                "https://example.edu.vn/qd3266.pdf", "Chương I\nĐiều 1. Phạm vi", "PUBLIC", LocalDate.of(2024, 8, 15));
        Document internal = new Document(UUID.randomUUID(), "Nội bộ", null, null, "x", "INTERNAL", null);
        when(documents.activeDocuments()).thenReturn(List.of(rule, internal));
        when(posts.getPublishedPosts(null, null)).thenReturn(List.of(post("PUBLIC")));

        List<Map<String, Object>> guest = provider.knowledge(false);

        assertThat(guest).hasSize(2);
        assertThat(guest.getFirst()).containsEntry("url", "https://example.edu.vn/qd3266.pdf")
                .containsEntry("updated_at", "15/08/2024").containsEntry("id", "doc-" + rule.id());
        assertThat(provider.knowledge(true)).hasSize(3);
    }

    @Test
    void jobMappingUsesAiVocabularyAndHidesZeroStipend() {
        JobPositionResponse job = JobPositionResponse.builder().id(UUID.randomUUID()).title("React Intern")
                .companyName("ABC").location("Cần Thơ").workFormat(WorkFormat.ONSITE).jobType(JobType.INTERNSHIP)
                .stipendAmount(BigDecimal.ZERO).description("x".repeat(3000))
                .skills(List.of(JobSkillResponse.builder().skillName("React").build())).build();

        Map<String, Object> row = AssistantKnowledgeProvider.toJob(job);

        assertThat(row.get("job_type")).isEqualTo("INTERN");
        assertThat(row.get("stipend")).isNull();
        assertThat(row.get("skills")).isEqualTo(List.of("React"));
        assertThat(((String) row.get("description"))).hasSize(1500);
        assertThat(row.get("url")).isEqualTo("/jobs/" + job.getId());
    }

    @Test
    void noApprovedJobsMeansNoTrendSnapshot() {
        when(trends.latestTerms()).thenReturn(List.of());
        assertThat(provider.trendSnapshot()).isNull();
    }
}
