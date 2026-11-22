package com.internlink.core.application.placement.dossier;

import com.internlink.core.application.placement.InternshipReportService;
import com.internlink.core.application.system.notification.PlacementWorkflowNotificationHelper;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.JpaDocumentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipReportRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.security.SecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlacementDossierReviewServiceTest {

    @Mock private JpaInternshipPlacementRepository placementRepository;
    @Mock private JpaDocumentRepository documentRepository;
    @Mock private JpaInternshipReportRepository reportRepository;
    @Mock private JpaStudentFoundApplicationRepository acceptanceFormRepository;
    @Mock private JpaUserRepository userRepository;
    @Mock private SecurityGuard securityGuard;
    @Mock private InternshipReportService reportService;
    @Mock private PlacementWorkflowNotificationHelper notifications;

    private PlacementDossierReviewService service;
    private InternshipPlacement placement;
    private User lecturer;
    private Document agreement;

    @BeforeEach
    void setUp() {
        DossierInspector inspector = new DossierInspector(documentRepository, reportRepository, acceptanceFormRepository);
        service = new PlacementDossierReviewService(placementRepository, documentRepository, reportRepository,
            userRepository, securityGuard, inspector, new DossierResponseMapper(), reportService, notifications);

        lecturer = User.builder().role(UserRole.LECTURER).fullName("GVHD A").build();
        lecturer.setId(UUID.randomUUID());
        placement = InternshipPlacement.builder().lecturer(lecturer).build();
        placement.setId(UUID.randomUUID());

        agreement = Document.builder().documentType(DocumentType.AGREEMENT).status("ACTIVE")
            .contextType(ContextType.PLACEMENT).contextId(placement.getId()).originalName("M01.pdf").build();
        agreement.setId(UUID.randomUUID());
        agreement.setCreatedAt(OffsetDateTime.now());

        when(placementRepository.findById(placement.getId())).thenReturn(Optional.of(placement));
        when(documentRepository.findById(agreement.getId())).thenReturn(Optional.of(agreement));
        when(documentRepository.findByContextTypeAndContextId(ContextType.PLACEMENT, placement.getId()))
            .thenReturn(List.of(agreement));
        actAs(lecturer);
    }

    private void actAs(User user) {
        CustomUserDetail principal = mock(CustomUserDetail.class);
        when(principal.getId()).thenReturn(user.getId());
        when(securityGuard.currentUser()).thenReturn(principal);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    void supervisorApprovesFileAndStudentIsNotified() {
        var response = service.review(placement.getId(), agreement.getId(), "approved", null);

        assertThat(agreement.getReviewStatus()).isEqualTo("APPROVED");
        assertThat(agreement.getReviewedBy()).isSameAs(lecturer);
        assertThat(response.getApprovedCount()).isEqualTo(1);
        verify(notifications).dossierReviewed(placement, lecturer.getId(), "M01", true, null);
    }

    @Test
    void revisionRequiresReason() {
        assertThatThrownBy(() -> service.review(placement.getId(), agreement.getId(), "REVISION_REQUIRED", "  "))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("lý do");
        verify(documentRepository, never()).save(any());
    }

    @Test
    void revisionStoresReasonForStudent() {
        service.review(placement.getId(), agreement.getId(), "REVISION_REQUIRED", "Thiếu chữ ký cán bộ hướng dẫn");

        assertThat(agreement.getReviewStatus()).isEqualTo("REVISION_REQUIRED");
        assertThat(agreement.getReviewNote()).isEqualTo("Thiếu chữ ký cán bộ hướng dẫn");
        verify(notifications).dossierReviewed(eq(placement), eq(lecturer.getId()), eq("M01"), eq(false), anyString());
    }

    @Test
    void otherLecturerCannotReview() {
        User other = User.builder().role(UserRole.LECTURER).build();
        other.setId(UUID.randomUUID());
        actAs(other);

        assertThatThrownBy(() -> service.review(placement.getId(), agreement.getId(), "APPROVED", null))
            .isNotInstanceOf(BadRequestException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void fileOfAnotherPlacementIsRejected() {
        agreement.setContextId(UUID.randomUUID());

        assertThatThrownBy(() -> service.review(placement.getId(), agreement.getId(), "APPROVED", null))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void olderSubmissionCannotBeReviewed() {
        Document newer = Document.builder().documentType(DocumentType.AGREEMENT).status("ACTIVE")
            .contextType(ContextType.PLACEMENT).contextId(placement.getId()).build();
        newer.setId(UUID.randomUUID());
        newer.setCreatedAt(OffsetDateTime.now().plusMinutes(5));
        when(documentRepository.findByContextTypeAndContextId(ContextType.PLACEMENT, placement.getId()))
            .thenReturn(List.of(agreement, newer));

        assertThatThrownBy(() -> service.review(placement.getId(), agreement.getId(), "APPROVED", null))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("mới nhất");
    }
}
