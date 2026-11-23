package com.internlink.core.application.ai_matching.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.internlink.core.application.placement.WeeklyLogbookService;
import com.internlink.core.application.system.DocumentService;
import com.internlink.core.domain.ai_matching.*;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.organization.*;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.ai_matching.dto.request.CvExtractionRequest;
import com.internlink.core.presentation.system.dto.response.DocumentDownloadInfo;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.util.*;

@ExtendWith(MockitoExtension.class)
class AiWorkspaceServiceTest {
    @Mock SecurityGuard guard;
    @Mock JpaUserRepository users;
    @Mock JpaDocumentRepository documents;
    @Mock DocumentService documentService;
    @Mock AiCvProcessingHandler cvHandler;
    @Mock JpaSkillTaxonomyRepository taxonomy;
    @Mock JpaStudentSkillRepository skills;
    @Mock JpaStudentProfileRepository profiles;
    @Mock JpaInternshipTermRepository terms;
    @Mock JpaStudentRosterRepository rosters;
    @Mock JpaJobPositionRepository jobs;
    @Mock JpaJobSkillRepository jobSkills;
    @Mock JpaInternshipPlacementRepository placements;
    @Mock WeeklyLogbookService logbooks;
    @Mock AiServiceClient client;
    @Spy AiMatchingMapper mapper = new AiMatchingMapper();
    @Mock AiRunNotificationHelper aiRunNotificationHelper;
    @Mock com.internlink.core.application.organization.TermDepartmentScope termScope;
    @InjectMocks AiWorkspaceService service;

    User actor(UserRole role) {
        User user = User.builder().role(role).fullName("Test").build();
        user.setId(UUID.randomUUID());
        when(guard.currentUser()).thenReturn(CustomUserDetail.create(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void consentRequiredBeforeAnyDocumentRead() {
        actor(UserRole.STUDENT);
        assertThatThrownBy(
                        () ->
                                service.extractCv(
                                        new CvExtractionRequest(null, "Java", false, false)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(documentService, cvHandler, client);
    }

    @Test
    void foreignDocumentIsRejectedBeforeDownload() {
        actor(UserRole.STUDENT);
        UUID id = UUID.randomUUID();
        User owner = User.builder().build();
        owner.setId(UUID.randomUUID());
        when(documents.findById(id))
                .thenReturn(
                        Optional.of(
                                Document.builder()
                                        .owner(owner)
                                        .documentType(DocumentType.CV)
                                        .build()));
        assertThatThrownBy(() -> service.extractCv(new CvExtractionRequest(id, null, true, false)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(documentService, cvHandler);
    }

    @Test
    void ownDocumentIsPassedAsBoundedBytesWithConsent() {
        User user = actor(UserRole.STUDENT);
        UUID id = UUID.randomUUID();
        Document document = Document.builder().owner(user).documentType(DocumentType.CV).build();
        document.setId(id);
        when(documents.findById(id)).thenReturn(Optional.of(document));
        when(documentService.downloadDocument(id, user.getId()))
                .thenReturn(
                        DocumentDownloadInfo.builder()
                                .resource(
                                        new ByteArrayResource(
                                                "Java"
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8)))
                                .sizeBytes(4L)
                                .build());
        AiRun run =
                AiRun.builder()
                        .id(UUID.randomUUID())
                        .outputResult(Map.of("status", "COMPLETED"))
                        .build();
        when(cvHandler.processCvSkills(eq(user.getId()), eq(id), eq("[document]"), any()))
                .thenReturn(new AiCvProcessingHandler.CvProcessingResult(run, List.of()));
        assertThat(service.extractCv(new CvExtractionRequest(id, null, true, false)))
                .containsEntry("status", "COMPLETED");
        verify(cvHandler)
                .processCvSkills(
                        eq(user.getId()),
                        eq(id),
                        eq("[document]"),
                        argThat(
                                context ->
                                        context.get("document_base64").equals("SmF2YQ==")
                                                && context.get("allow_cloud").equals(false)));
    }

    @Test
    void manualEvidenceOverridesNegatedProposalWithoutDeletingSource() {
        User user = actor(UserRole.STUDENT);
        SkillTaxonomy java = SkillTaxonomy.builder().id("SK-JAVA").skillName("Java").build();
        StudentSkill row =
                StudentSkill.builder()
                        .id(new StudentSkillId(user.getId(), "SK-JAVA"))
                        .student(user)
                        .skill(java)
                        .source(SkillSource.CV_AI)
                        .aiEvidence(Map.of("sourceHash", "old", "assertionStatus", "negated"))
                        .build();
        when(taxonomy.findById("SK-JAVA")).thenReturn(Optional.of(java));
        when(skills.findById(row.getId())).thenReturn(Optional.of(row));
        when(skills.save(row)).thenReturn(row);
        var result = service.declareSkill("SK-JAVA", "Built a Java application");
        assertThat(result.getIsConfirmed()).isTrue();
        assertThat(result.getSource()).isEqualTo(SkillSource.STUDENT_DECLARED);
        assertThat(result.getAiEvidence())
                .containsEntry("sourceHash", "old")
                .containsEntry("studentEvidence", "Built a Java application");
    }

    @Test
    void unrelatedCompanyCannotReviewJob() {
        User user = actor(UserRole.COMPANY_REP);
        Company own = Company.builder().build();
        own.setId(UUID.randomUUID());
        user.setCompany(own);
        Company other = Company.builder().build();
        other.setId(UUID.randomUUID());
        Department department = Department.builder().build();
        department.setId(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        when(jobs.findById(id))
                .thenReturn(
                        Optional.of(
                                JobPosition.builder()
                                        .company(other)
                                        .build()));
        assertThatThrownBy(() -> service.reviewJob(id)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(client);
    }

    @Test
    void smallCohortDoesNotReadIndividualSkills() {
        User user = actor(UserRole.FACULTY_ADMIN);
        Department department = Department.builder().build();
        department.setId(UUID.randomUUID());
        user.setDepartment(department);
        UUID id = UUID.randomUUID();
        when(terms.findById(id))
                .thenReturn(Optional.of(InternshipTerm.builder().build()));
        when(termScope.rosters(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(StudentRoster.builder().claimedUser(user).build()));
        assertThat(service.skillGaps(id)).containsEntry("suppressed", true);
        verifyNoInteractions(skills, client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void skillNobodyHasIsShownAsZeroWhileSmallGroupsStayHidden() {
        User admin = actor(UserRole.FACULTY_ADMIN);
        Department department = Department.builder().build();
        department.setId(UUID.randomUUID());
        admin.setDepartment(department);
        UUID termId = UUID.randomUUID();
        when(terms.findById(termId)).thenReturn(Optional.of(InternshipTerm.builder().build()));
        List<StudentRoster> roster = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            User student = User.builder().role(UserRole.STUDENT).build();
            student.setId(UUID.randomUUID());
            roster.add(StudentRoster.builder().claimedUser(student).build());
        }
        when(termScope.rosters(eq(termId), any())).thenReturn(roster);
        SkillTaxonomy flutter = SkillTaxonomy.builder().id("SK-FLUTTER").skillName("Flutter").build();
        SkillTaxonomy csharp = SkillTaxonomy.builder().id("SK-CSHARP").skillName("C#").build();
        when(skills.findByIdStudentIdAndIsConfirmedTrue(any())).thenReturn(List.of());
        UUID firstStudent = roster.get(0).getClaimedUser().getId();
        UUID secondStudent = roster.get(1).getClaimedUser().getId();
        for (UUID id : List.of(firstStudent, secondStudent))
            when(skills.findByIdStudentIdAndIsConfirmedTrue(id))
                    .thenReturn(List.of(StudentSkill.builder().skill(csharp).build()));
        JobPosition job = JobPosition.builder().build();
        job.setId(UUID.randomUUID());
        when(jobs.findByTermIdAndStatus(termId, JobStatus.APPROVED)).thenReturn(List.of(job));
        when(jobSkills.findByIdJobIdIn(List.of(job.getId())))
                .thenReturn(
                        List.of(
                                com.internlink.core.domain.recruitment.JobSkill.builder()
                                        .skill(flutter)
                                        .requirementType(RequirementType.MANDATORY)
                                        .build(),
                                com.internlink.core.domain.recruitment.JobSkill.builder()
                                        .skill(csharp)
                                        .requirementType(RequirementType.MANDATORY)
                                        .build()));
        when(taxonomy.findAll()).thenReturn(List.of(flutter, csharp));

        var rows = (List<Map<String, Object>>) service.skillGaps(termId).get("skills");

        assertThat(rows)
                .filteredOn(r -> r.get("id").equals("SK-FLUTTER"))
                .singleElement()
                .satisfies(r -> assertThat(r.get("confirmedStudents")).isEqualTo(0L));
        assertThat(rows)
                .filteredOn(r -> r.get("id").equals("SK-CSHARP"))
                .singleElement()
                .satisfies(r -> assertThat(r.get("confirmedStudents")).isNull());
    }

    @Test
    void journalAuthorizationRunsBeforeInference() {
        UUID id = UUID.randomUUID();
        when(logbooks.getLogbooksByPlacement(id)).thenThrow(new ForbiddenException("Denied"));
        assertThatThrownBy(() -> service.summarize(id)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(client);
    }
}
