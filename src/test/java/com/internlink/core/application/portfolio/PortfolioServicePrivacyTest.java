package com.internlink.core.application.portfolio;

import static com.internlink.core.application.portfolio.PortfolioTestData.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.internlink.core.application.placement.InternshipReportService;
import com.internlink.core.application.system.DocumentService;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServicePrivacyTest {
    @Mock JpaInternshipPlacementRepository placements;
    @Mock JpaUserRepository users;
    @Mock JpaStudentRosterRepository rosters;
    @Mock JpaPortfolioFormRepository forms;
    @Mock JpaPortfolioRevisionRepository revisions;
    @Mock JpaPortfolioSignedFileRepository signedFiles;
    @Mock JpaInternshipReportRepository reports;
    @Mock SecurityGuard security;
    @Mock DailyJournalService daily;
    @Mock PortfolioDocxExporter exporter;
    @Mock PortfolioMediaResolver media;
    @Mock DocumentService documents;
    @Mock InternshipReportService reportService;
    @Mock PortfolioScoring scoring;
    PortfolioViewMapper viewMapper;
    PortfolioMetadataHelper metadataHelper;
    PortfolioService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        viewMapper = new PortfolioViewMapper(revisions, signedFiles);
        metadataHelper = new PortfolioMetadataHelper(rosters);
        var fileStorage = mock(PortfolioFileStorage.class);
        var formSupport = new PortfolioFormSupport(forms, revisions, daily, metadataHelper, viewMapper);
        var signedWorkflow = new PortfolioSignedFileWorkflow(
                placements, users, forms, revisions, signedFiles, security,
                viewMapper, fileStorage, formSupport);
        service =
                new PortfolioService(
                        placements,
                        users,
                        forms,
                        revisions,
                        reports,
                        security,
                        daily,
                        exporter,
                        media,
                        documents,
                        reportService,
                        scoring,
                        metadataHelper,
                        viewMapper,
                        formSupport,
                        signedWorkflow,
                        mock(com.internlink.core.application.placement.dossier.DossierInspector.class),
                        mock(
                                com.internlink.core.application.system.notification
                                        .PlacementWorkflowNotificationHelper.class));
    }

    @Test
    void secretFormCannotBeDownloadedByGuessingRevisionOrSignedFileId() {
        var p = placement();
        var f = form(p, "M03");
        f.setStatus("COMPLETED");
        when(placements.findById(p.getId())).thenReturn(Optional.of(p));
        when(forms.findByPlacementIdAndKind(p.getId(), "M03")).thenReturn(Optional.of(f));
        when(security.currentUser()).thenReturn(CustomUserDetail.create(p.getStudent()));
        when(users.findById(p.getStudent().getId())).thenReturn(Optional.of(p.getStudent()));
        assertThatThrownBy(() -> service.export(p.getId(), "M03", UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.signed(p.getId(), "M03", UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(revisions, signedFiles, exporter);
    }

    @Test
    void signedUploadReturnsTheManagedIdEvenWhenJpaMergesTheNewEntity() throws Exception {
        var p = placement();
        var f = form(p, "M03");
        f.setStatus("COMPLETED");
        when(placements.findLockedById(p.getId())).thenReturn(Optional.of(p));
        when(forms.findByPlacementIdAndKind(p.getId(), "M03")).thenReturn(Optional.of(f));
        when(security.currentUser()).thenReturn(CustomUserDetail.create(p.getMentor()));
        when(users.findById(p.getMentor().getId())).thenReturn(Optional.of(p.getMentor()));
        var revision = new com.internlink.core.domain.placement.PortfolioRevision();
        revision.setId(UUID.randomUUID());
        revision.setForm(f);
        when(revisions.findFirstByFormIdAndDocxIsNotNullOrderByCreatedAtDesc(f.getId()))
                .thenReturn(Optional.of(revision));
        UUID fileId = UUID.randomUUID();
        when(signedFiles.saveAndFlush(any()))
                .thenAnswer(
                        invocation -> {
                            var managed =
                                    new com.internlink.core.domain.placement.PortfolioSignedFile();
                            org.springframework.beans.BeanUtils.copyProperties(
                                    invocation.getArgument(0), managed);
                            managed.setId(fileId);
                            return managed;
                        });
        var result =
                service.uploadSigned(
                        p.getId(),
                        "M03",
                        null,
                        new org.springframework.mock.web.MockMultipartFile(
                                "file",
                                "signed.pdf",
                                "application/pdf",
                                "%PDF-1.7 fixture".getBytes()));
        assertThat(result.id()).isEqualTo(fileId);
        assertThat(result.revisionId()).isEqualTo(revision.getId());
    }
}
