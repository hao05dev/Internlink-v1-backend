package com.internlink.core.application.evaluation.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.internlink.core.application.evaluation.ComponentScoreService;
import com.internlink.core.application.evaluation.PaperEvaluationService;
import com.internlink.core.application.system.DocumentService;
import com.internlink.core.application.system.notification.PlacementWorkflowNotificationHelper;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.evaluation.PaperEvaluationScan;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.evaluation.dto.request.PaperScanConfirmRequest;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.security.SecurityGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PaperScanServiceTest {
    @Mock JpaPaperEvaluationScanRepository scans;
    @Mock JpaInternshipPlacementRepository placements;
    @Mock JpaDocumentRepository documents;
    @Mock JpaUserRepository users;
    @Mock JpaStudentProfileRepository studentProfiles;
    @Mock SecurityGuard security;
    @Mock DocumentService documentService;
    @Mock AiServiceClient ai;
    @Mock ComponentScoreService componentScores;
    @Mock PaperEvaluationService paperEvaluations;
    @Mock PlacementWorkflowNotificationHelper notifications;

    private PaperScanService service() {
        return new PaperScanService(scans, placements, documents, users, studentProfiles, security,
                documentService, ai, componentScores, paperEvaluations, notifications);
    }

    @Test
    void confirmingScanAlsoApprovesM03InDossier() {
        User lecturer = User.builder().role(UserRole.LECTURER).build();
        lecturer.setId(UUID.randomUUID());
        InternshipPlacement placement = InternshipPlacement.builder().lecturer(lecturer).build();
        placement.setId(UUID.randomUUID());
        Document sheet = Document.builder().contextType(ContextType.PLACEMENT).contextId(placement.getId())
                .documentType(DocumentType.EXTERNAL_EVALUATION).status("ACTIVE").build();
        sheet.setId(UUID.randomUUID());
        PaperEvaluationScan scan = PaperEvaluationScan.builder().placement(placement).document(sheet)
                .scannedBy(lecturer).reading(Map.of("criteria", List.of())).build();
        scan.setId(UUID.randomUUID());

        when(security.currentUser()).thenReturn(CustomUserDetail.create(lecturer));
        when(users.findById(lecturer.getId())).thenReturn(Optional.of(lecturer));
        when(scans.findById(scan.getId())).thenReturn(Optional.of(scan));
        when(placements.findById(placement.getId())).thenReturn(Optional.of(placement));
        when(scans.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service().confirm(scan.getId(),
                new PaperScanConfirmRequest(Map.of("I.4", new BigDecimal("8")), null, List.of(), null));

        assertThat(result.confirmedScore()).isEqualByComparingTo("8.0");
        assertThat(sheet.getReviewStatus()).isEqualTo("APPROVED");
        assertThat(sheet.getReviewedBy()).isSameAs(lecturer);
        assertThat(sheet.getReviewedAt()).isNotNull();
        verify(documents).save(sheet);
        verify(notifications).dossierReviewed(placement, lecturer.getId(), "M03", true, null);
    }
}
