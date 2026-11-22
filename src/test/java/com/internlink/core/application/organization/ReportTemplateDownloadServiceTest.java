package com.internlink.core.application.organization;

import com.internlink.core.domain.organization.ReportTemplateVersion;
import com.internlink.core.infrastructure.integration.storage.OfficialTemplateStore;
import com.internlink.core.infrastructure.integration.storage.StorageService;
import com.internlink.core.infrastructure.integration.storage.StorageServiceRouter;
import com.internlink.core.infrastructure.persistence.jpa.JpaReportTemplateVersionRepository;
import com.internlink.core.shared.enums.ReportTemplateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.internlink.core.application.organization.TemplateTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportTemplateDownloadServiceTest {

    @Mock private JpaReportTemplateVersionRepository templateRepository;
    @Mock private StorageServiceRouter storageServiceRouter;
    @Mock private StorageService storageService;
    @Mock private OfficialTemplateStore officialTemplateStore;

    private ReportTemplateDownloadService service;

    @BeforeEach
    void setUp() {
        service = new ReportTemplateDownloadService(
            templateRepository, storageServiceRouter,
            new SampleTemplateDocumentGenerator(officialTemplateStore),
            new TemplateSelector(templateRepository));
    }

    @Test
    void loadTemplateDownload_whenStorageSucceeds_returnsStoredResource() {
        UUID id = UUID.randomUUID();
        ReportTemplateVersion entity = ReportTemplateVersion.builder()
            .title("M-TT-04 KHMT")
            .reportType(ReportTemplateType.M04)
            .fileUrl("r2://bucket/internlink/templates/term1/m04/cs/file1.docx")
            .fileName("M-TT-04.docx")
            .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .build();

        when(templateRepository.findById(id)).thenReturn(Optional.of(entity));
        when(storageServiceRouter.getActiveStorageService()).thenReturn(storageService);
        when(storageService.loadAsResource("internlink/templates/term1/m04/cs/file1.docx"))
            .thenReturn(new ByteArrayResource("test-docx-content".getBytes()));

        TemplateDownload result = service.loadTemplateDownload(id);

        assertThat(result.fileName()).isEqualTo("M-TT-04.docx");
        assertThat(result.mimeType()).contains("wordprocessingml");
        assertThat(result.resource()).isNotNull();
    }

    @Test
    void loadTemplateDownload_whenStorageFails_fallsBackToGeneratedSample() {
        UUID id = UUID.randomUUID();
        ReportTemplateVersion entity = ReportTemplateVersion.builder()
            .title("M-TT-04 Mock")
            .reportType(ReportTemplateType.M04)
            .programCode("CS")
            .fileUrl("/api/v1/storage/documents/sample/M-TT-04.doc")
            .fileName("M-TT-04.doc")
            .mimeType("application/msword")
            .build();

        when(templateRepository.findById(id)).thenReturn(Optional.of(entity));

        TemplateDownload result = service.loadTemplateDownload(id);

        assertThat(result.fileName()).isEqualTo("M-TT-04.doc");
        assertThat(result.resource()).isNotNull();
        verifyNoInteractions(storageServiceRouter);
    }

    @Test
    void loadTemplateDownload_whenNoStoredFile_usesOfficialTemplateFromR2() {
        UUID id = UUID.randomUUID();
        ReportTemplateVersion entity = ReportTemplateVersion.builder()
            .title("M-TT-05")
            .reportType(ReportTemplateType.M05)
            .fileUrl("/api/v1/storage/documents/sample/M-TT-05.doc")
            .fileName("M-TT-05-Maubaocao.doc")
            .mimeType("application/msword")
            .build();
        ByteArrayResource official = new ByteArrayResource("official-r2".getBytes());

        when(templateRepository.findById(id)).thenReturn(Optional.of(entity));
        when(officialTemplateStore.find("M-TT-05-Maubaocao.doc")).thenReturn(Optional.of(official));

        TemplateDownload result = service.loadTemplateDownload(id);

        assertThat(result.resource()).isSameAs(official);
        verifyNoInteractions(storageServiceRouter);
    }

    @Test
    void generateFallbackDownload_createsOfficialFormattedDoc() {
        TemplateDownload result = service.generateFallbackDownload(ReportTemplateType.M05, "DEFAULT");

        assertThat(result.fileName()).isEqualTo("M-TT-05-Maubaocao.doc");
        assertThat(result.mimeType()).isEqualTo("application/msword");
        assertThat(result.resource()).isNotNull();
    }

    @Test
    void loadForAudience_returnsFileOfStudentsFaculty() {
        UUID term = UUID.randomUUID();
        ReportTemplateVersion khmt = template(KHMT, ReportTemplateType.FORM_04_RECEPTION_8W, "DEFAULT");
        khmt.setFileName("04-KHMT.doc");
        ReportTemplateVersion mmt = template(MMT, ReportTemplateType.FORM_04_RECEPTION_8W, "DEFAULT");
        mmt.setFileName("04-MMT.doc");
        when(templateRepository.findByTermIdAndReportTypeAndIsActiveTrue(term, ReportTemplateType.FORM_04_RECEPTION_8W))
            .thenReturn(List.of(khmt, mmt));
        when(storageServiceRouter.getActiveStorageService()).thenReturn(storageService);
        when(storageService.loadAsResource(anyString())).thenReturn(new ByteArrayResource("x".getBytes()));

        TemplateDownload result = service.loadForAudience("FORM_04", null,
            new TemplateAudience(term, MMT.getId(), "NE", 3));

        assertThat(result.fileName()).isEqualTo("04-MMT.doc");
    }

    @Test
    void loadForAudience_withoutAnyMatchingForm_generatesSample() {
        when(templateRepository.findByReportTypeAndIsActiveTrue(ReportTemplateType.M02))
            .thenReturn(List.of(template(MMT, ReportTemplateType.M02, "DEFAULT")));

        TemplateDownload result = service.loadForAudience("M02", null,
            new TemplateAudience(null, KHMT.getId(), "CS", null));

        assertThat(result.mimeType()).isEqualTo("application/msword");
        assertThat(result.resource()).isNotNull();
        verifyNoInteractions(storageServiceRouter);
    }
}
