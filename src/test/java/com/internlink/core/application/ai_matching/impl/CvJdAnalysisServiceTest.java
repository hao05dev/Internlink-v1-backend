package com.internlink.core.application.ai_matching.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.internlink.core.application.system.DocumentService;
import com.internlink.core.domain.ai_matching.SkillTaxonomy;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.recruitment.JobSkill;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.ai_matching.dto.request.CvJdAnalysisRequest;
import com.internlink.core.presentation.system.dto.response.DocumentDownloadInfo;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class CvJdAnalysisServiceTest {
    @Mock SecurityGuard guard;
    @Mock JpaUserRepository users;
    @Mock JpaDocumentRepository documents;
    @Mock DocumentService documentService;
    @Mock JpaJobPositionRepository jobs;
    @Mock JpaJobSkillRepository jobSkills;
    @Mock AiServiceClient client;
    @InjectMocks CvJdAnalysisService service;

    User student() {
        User user = User.builder().role(UserRole.STUDENT).fullName("Test").build();
        user.setId(UUID.randomUUID());
        when(guard.currentUser()).thenReturn(CustomUserDetail.create(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    JobPosition job(JobStatus status) {
        JobPosition job = JobPosition.builder().title("Backend Intern").description("<p>Yêu cầu Java</p><ul><li>Docker</li></ul>").status(status).build();
        job.setId(UUID.randomUUID());
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }

    Document cv(User owner) {
        Document document = Document.builder().owner(owner).documentType(DocumentType.CV).status("ACTIVE").build();
        document.setId(UUID.randomUUID());
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        return document;
    }

    @Test
    void consentIsRequiredBeforeReadingAnything() {
        assertThatThrownBy(() -> service.analyze(new CvJdAnalysisRequest(UUID.randomUUID(), UUID.randomUUID(), false)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(documents, documentService, client);
    }

    @Test
    void unpublishedJobIsRejected() {
        student();
        JobPosition job = job(JobStatus.DRAFT);
        assertThatThrownBy(() -> service.analyze(new CvJdAnalysisRequest(job.getId(), UUID.randomUUID(), true)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(documentService, client);
    }

    @Test
    void anotherStudentsCvIsRejectedBeforeDownload() {
        student();
        JobPosition job = job(JobStatus.APPROVED);
        User other = User.builder().build();
        other.setId(UUID.randomUUID());
        Document document = cv(other);
        assertThatThrownBy(() -> service.analyze(new CvJdAnalysisRequest(job.getId(), document.getId(), true)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(documentService, client);
    }

    @Test
    void ownCvIsSentWithJobRequirementsAndPlainDescription() {
        User user = student();
        JobPosition job = job(JobStatus.APPROVED);
        Document document = cv(user);
        when(documentService.downloadDocument(document.getId(), user.getId())).thenReturn(DocumentDownloadInfo.builder()
                .resource(new ByteArrayResource("Java".getBytes(StandardCharsets.UTF_8)))
                .originalName("cv-an.pdf").sizeBytes(4L).build());
        SkillTaxonomy java = SkillTaxonomy.builder().id("SK-JAVA").skillName("Java").build();
        SkillTaxonomy docker = SkillTaxonomy.builder().id("SK-DOCKER").skillName("Docker").build();
        when(jobSkills.findByIdJobId(job.getId())).thenReturn(List.of(
                JobSkill.builder().skill(java).requirementType(RequirementType.MANDATORY).build(),
                JobSkill.builder().skill(docker).requirementType(RequirementType.OPTIONAL).build()));
        when(client.infer(eq(CvJdAnalysisService.ENDPOINT), any())).thenReturn(Map.of("status", "COMPLETED"));

        var result = service.analyze(new CvJdAnalysisRequest(job.getId(), document.getId(), true));

        assertThat(result).containsEntry("status", "COMPLETED").containsEntry("jobId", job.getId())
                .containsEntry("documentName", "cv-an.pdf");
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(client).infer(eq(CvJdAnalysisService.ENDPOINT), payload.capture());
        assertThat(payload.getValue()).containsEntry("filename", "cv-an.pdf").containsEntry("content_base64", "SmF2YQ==")
            .containsEntry("include_github", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> sentJob = (Map<String, Object>) payload.getValue().get("job");
        assertThat(sentJob.get("description")).isEqualTo("Yêu cầu Java\nDocker");
        assertThat(sentJob.get("requirements")).asList().containsExactly(
                Map.of("skill_id", "SK-JAVA", "skill_name", "Java", "requirement_type", "mandatory"),
                Map.of("skill_id", "SK-DOCKER", "skill_name", "Docker", "requirement_type", "optional"));
    }

    @Test
    void filenameFallsBackToMimeType() {
        assertThat(CvJdAnalysisService.filename(DocumentDownloadInfo.builder().originalName("CV final").mimeType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document").build())).isEqualTo("cv.docx");
        assertThat(CvJdAnalysisService.filename(DocumentDownloadInfo.builder().mimeType("application/pdf").build()))
                .isEqualTo("cv.pdf");
    }
}
