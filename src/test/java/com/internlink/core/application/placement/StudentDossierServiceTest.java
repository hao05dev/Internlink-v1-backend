package com.internlink.core.application.placement;

import com.internlink.core.application.placement.impl.StudentDossierServiceImpl;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.integration.storage.StorageServiceRouter;
import com.internlink.core.infrastructure.persistence.jpa.JpaDocumentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipReportRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.application.placement.dossier.DossierInspector;
import com.internlink.core.application.placement.dossier.DossierResponseMapper;
import com.internlink.core.application.system.notification.PlacementWorkflowNotificationHelper;
import com.internlink.core.presentation.placement.dto.response.StudentDossierResponse;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentDossierServiceTest {

    @Mock
    private JpaInternshipPlacementRepository placementRepository;

    @Mock
    private JpaDocumentRepository documentRepository;

    @Mock
    private JpaInternshipReportRepository reportRepository;

    @Mock
    private JpaStudentFoundApplicationRepository acceptanceFormRepository;

    @Mock
    private PlacementWorkflowNotificationHelper notifications;

    @Mock
    private StorageServiceRouter storageServiceRouter;

    private StudentDossierServiceImpl dossierService;

    @BeforeEach
    void setUp() {
        dossierService = new StudentDossierServiceImpl(
            placementRepository,
            documentRepository,
            reportRepository,
            storageServiceRouter,
            new DossierInspector(documentRepository, reportRepository, acceptanceFormRepository),
            new DossierResponseMapper(),
            notifications
        );
    }

    @Test
    void getStudentDossierComputesMissingAndSubmittedAccurately() {
        UUID studentId = UUID.randomUUID();
        User student = User.builder().email("student@ctu.edu.vn").role(UserRole.STUDENT).build();
        student.setId(studentId);

        UUID placementId = UUID.randomUUID();
        Company company = Company.builder().companyName("FPT Software").build();
        InternshipPlacement placement = InternshipPlacement.builder()
            .student(student)
            .company(company)
            .status(PlacementStatus.ACTIVE)
            .build();
        placement.setId(placementId);

        Document doc04 = Document.builder()
            .documentType(DocumentType.ACCEPTANCE_LETTER)
            .status("ACTIVE")
            .originalName("04-Phieu-nhan-SV.pdf")
            .build();
        Document docM01 = Document.builder()
            .documentType(DocumentType.AGREEMENT)
            .status("ACTIVE")
            .originalName("M-TT-01-Phieugiaoviec.docx")
            .build();

        when(placementRepository.findByStudentId(studentId)).thenReturn(List.of(placement));
        when(documentRepository.findByContextTypeAndContextId(eq(ContextType.PLACEMENT), eq(placementId)))
            .thenReturn(List.of(doc04, docM01));

        StudentDossierResponse response = dossierService.getStudentDossier(student);

        assertThat(response).isNotNull();
        assertThat(response.getPlacementId()).isEqualTo(placementId);
        assertThat(response.getCompanyName()).isEqualTo("FPT Software");
        assertThat(response.getTotalRequired()).isEqualTo(5);
        assertThat(response.getSubmittedCount()).isEqualTo(2);
        assertThat(response.getMissingCount()).isEqualTo(3);
        assertThat(response.getCompletionPercentage()).isEqualTo(40);

        var item04 = response.getItems().stream().filter(i -> "FORM_04".equals(i.getTemplateCode())).findFirst().orElseThrow();
        assertThat(item04.isSubmitted()).isTrue();
        // Nộp xong là chờ GVHD duyệt, không còn tự động "đạt".
        assertThat(item04.getStatus()).isEqualTo("PENDING");
        assertThat(response.getApprovedCount()).isZero();
        assertThat(response.isComplete()).isFalse();
        assertThat(item04.getSubmittedFileName()).isEqualTo("04-Phieu-nhan-SV.pdf");

        var itemM03 = response.getItems().stream().filter(i -> "M03".equals(i.getTemplateCode())).findFirst().orElseThrow();
        assertThat(itemM03.isSubmitted()).isFalse();
        assertThat(itemM03.getStatus()).isEqualTo("MISSING");
    }

    @Test
    void getStudentDossierWhenNoPlacementReturnsStandardMissingItems() {
        UUID studentId = UUID.randomUUID();
        User student = User.builder().email("student2@ctu.edu.vn").role(UserRole.STUDENT).build();
        student.setId(studentId);

        when(placementRepository.findByStudentId(studentId)).thenReturn(List.of());

        StudentDossierResponse response = dossierService.getStudentDossier(student);

        assertThat(response).isNotNull();
        assertThat(response.getPlacementId()).isNull();
        assertThat(response.getTotalRequired()).isEqualTo(5);
        assertThat(response.getSubmittedCount()).isEqualTo(0);
        assertThat(response.getMissingCount()).isEqualTo(5);
        assertThat(response.getCompletionPercentage()).isEqualTo(0);
        assertThat(response.getItems()).hasSize(6);
    }
}
