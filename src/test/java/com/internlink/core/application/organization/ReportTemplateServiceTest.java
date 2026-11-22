package com.internlink.core.application.organization;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.ReportTemplateVersion;
import com.internlink.core.infrastructure.integration.storage.StorageService;
import com.internlink.core.infrastructure.integration.storage.StorageServiceRouter;
import com.internlink.core.infrastructure.integration.storage.StoredFileInfo;
import com.internlink.core.infrastructure.persistence.jpa.JpaAcademicProgramRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaDepartmentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaReportTemplateVersionRepository;
import com.internlink.core.presentation.organization.dto.request.UpdateReportTemplateRequest;
import com.internlink.core.shared.enums.ReportTemplateType;
import com.internlink.core.shared.enums.StorageProvider;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.internlink.core.application.organization.TemplateTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportTemplateServiceTest {

    @Mock private JpaReportTemplateVersionRepository templateRepository;
    @Mock private JpaInternshipTermRepository termRepository;
    @Mock private JpaDepartmentRepository departmentRepository;
    @Mock private JpaAcademicProgramRepository programRepository;
    @Mock private StorageServiceRouter storageServiceRouter;
    @Mock private StorageService storageService;

    private ReportTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ReportTemplateService(
            templateRepository, termRepository, programRepository, storageServiceRouter,
            new TemplateAccessPolicy(departmentRepository));
    }

    @Test
    void updateTemplate_updatesTitleAndFileNameSuccessfully() {
        UUID templateId = UUID.randomUUID();
        ReportTemplateVersion entity = template(KHMT, ReportTemplateType.M01, "DEFAULT");
        entity.setTitle("Old Title");
        entity.setId(templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(entity));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = UpdateReportTemplateRequest.builder()
            .title("New Title").fileName("new.doc").description("New Desc").build();

        var res = service.updateTemplate(templateId, req, facultyAdmin(KHMT));
        assertThat(res.title()).isEqualTo("New Title");
        assertThat(res.fileName()).isEqualTo("new.doc");
        assertThat(res.description()).isEqualTo("New Desc");
    }

    @Test
    void uploadTemplate_savesCleanR2Key() {
        UUID termId = UUID.randomUUID();
        stubStorage(termId, "internlink/templates/" + termId + "/khmt/m04/cs/uuid.docx");
        MockMultipartFile file = new MockMultipartFile("file", "test.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "bytes".getBytes());

        service.uploadTemplate(termId, null, null, "CS", ReportTemplateType.M04, "M04 Test", "Desc", true, file, facultyAdmin(KHMT));

        verify(templateRepository).save(argThat((ReportTemplateVersion entity) ->
            ("internlink/templates/" + termId + "/khmt/m04/cs/uuid.docx").equals(entity.getFileUrl())));
    }

    @Test
    void uploadTemplate_byFacultyAdmin_alwaysUsesOwnDepartment() {
        UUID termId = UUID.randomUUID();
        stubStorage(termId, "k/f.doc");
        MockMultipartFile file = new MockMultipartFile("file", "04.doc", "application/msword", "b".getBytes());

        service.uploadTemplate(termId, MMT.getId(), null, null, ReportTemplateType.FORM_04_RECEPTION_8W,
            null, null, true, file, facultyAdmin(KHMT));

        verify(templateRepository).save(argThat((ReportTemplateVersion t) -> t.getDepartment() == KHMT));
        verify(storageService).store(any(), contains("/khmt/"));
        verify(templateRepository).findFirstByTermIdAndDepartmentIdAndReportTypeAndProgramCodeOrderByVersionNumberDesc(
            termId, KHMT.getId(), ReportTemplateType.FORM_04_RECEPTION_8W, "DEFAULT");
    }

    @Test
    void uploadTemplate_byFacultyAdminWithoutDepartment_isRejected() {
        UUID termId = UUID.randomUUID();
        when(termRepository.findById(termId)).thenReturn(Optional.of(InternshipTerm.builder().build()));
        MockMultipartFile file = new MockMultipartFile("file", "04.doc", "application/msword", "b".getBytes());

        assertThatThrownBy(() -> service.uploadTemplate(termId, null, null, null, ReportTemplateType.M01,
            null, null, true, file, facultyAdmin(null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteTemplate_ofAnotherFaculty_isForbidden() {
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.of(template(MMT, ReportTemplateType.M01, "DEFAULT")));

        assertThatThrownBy(() -> service.deleteTemplate(id, facultyAdmin(KHMT)))
            .isInstanceOf(ForbiddenException.class);
        verify(templateRepository, never()).delete(any());
    }

    @Test
    void toggleSchoolWideTemplate_byFacultyAdmin_isForbidden() {
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.of(template(null, ReportTemplateType.M01, "DEFAULT")));

        assertThatThrownBy(() -> service.toggleActive(id, facultyAdmin(KHMT)))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteTemplate_byAdmin_allowedForAnyFaculty() {
        UUID id = UUID.randomUUID();
        ReportTemplateVersion mmtTemplate = template(MMT, ReportTemplateType.M01, "DEFAULT");
        when(templateRepository.findById(id)).thenReturn(Optional.of(mmtTemplate));

        service.deleteTemplate(id, User.builder().role(UserRole.ADMIN).build());

        verify(templateRepository).delete(mmtTemplate);
    }

    @Test
    void inheritTemplates_copiesOnlyOwnFacultyTemplates() {
        UUID sourceTermId = UUID.randomUUID();
        UUID targetTermId = UUID.randomUUID();
        ReportTemplateVersion own = template(KHMT, ReportTemplateType.M01, "DEFAULT");
        own.setTitle("M01-KHMT");
        ReportTemplateVersion other = template(MMT, ReportTemplateType.M01, "DEFAULT");
        ReportTemplateVersion school = template(null, ReportTemplateType.M01, "DEFAULT");

        when(termRepository.findById(targetTermId)).thenReturn(Optional.of(InternshipTerm.builder().build()));
        when(templateRepository.findByTermIdAndIsActiveTrue(sourceTermId)).thenReturn(List.of(own, other, school));
        when(templateRepository.findByTermIdAndIsActiveTrue(targetTermId)).thenReturn(List.of());
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.inheritTemplates(sourceTermId, targetTermId, false, facultyAdmin(KHMT));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("M01-KHMT");
        assertThat(result.get(0).departmentId()).isEqualTo(KHMT.getId());
    }

    @Test
    void inheritTemplates_throwsWhenSourceAndTargetSame() {
        UUID termId = UUID.randomUUID();

        assertThatThrownBy(() -> service.inheritTemplates(termId, termId, false, facultyAdmin(KHMT)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getTemplates_forFacultyAdmin_ignoresRequestedDepartment() {
        UUID termId = UUID.randomUUID();
        when(templateRepository.findVisibleToDepartment(termId, KHMT.getId(), false)).thenReturn(List.of());

        service.getTemplates(termId, MMT.getId(), false, facultyAdmin(KHMT));

        verify(templateRepository).findVisibleToDepartment(termId, KHMT.getId(), false);
        verify(templateRepository, never()).findByTermIdAndDepartmentIdOrderByCreatedAtDesc(any(), any());
    }

    private void stubStorage(UUID termId, String key) {
        when(termRepository.findById(termId)).thenReturn(Optional.of(InternshipTerm.builder().build()));
        when(storageServiceRouter.getActiveStorageService()).thenReturn(storageService);
        when(storageService.store(any(), anyString())).thenReturn(StoredFileInfo.builder()
            .provider(StorageProvider.R2).providerFileId(key).storagePathOrUrl("r2://bucket/" + key)
            .originalName("f").mimeType("application/msword").sizeBytes(1L).build());
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }
}
