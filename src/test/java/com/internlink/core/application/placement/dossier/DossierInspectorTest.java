package com.internlink.core.application.placement.dossier;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.AcceptanceFormStatus;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.InternshipReport;
import com.internlink.core.domain.placement.StudentFoundApplication;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.JpaDocumentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipReportRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DossierInspectorTest {

    @Mock private JpaDocumentRepository documentRepository;
    @Mock private JpaInternshipReportRepository reportRepository;
    @Mock private JpaStudentFoundApplicationRepository acceptanceFormRepository;

    private DossierInspector inspector;
    private InternshipPlacement placement;

    @BeforeEach
    void setUp() {
        inspector = new DossierInspector(documentRepository, reportRepository, acceptanceFormRepository);
        placement = InternshipPlacement.builder().build();
        placement.setId(UUID.randomUUID());
    }

    private static Document doc(DocumentType type, String reviewStatus, OffsetDateTime createdAt) {
        Document d = Document.builder().documentType(type).status("ACTIVE").reviewStatus(reviewStatus)
            .originalName(type + ".pdf").build();
        d.setId(UUID.randomUUID());
        d.setCreatedAt(createdAt);
        return d;
    }

    private void docs(Document... list) {
        when(documentRepository.findByContextTypeAndContextId(ContextType.PLACEMENT, placement.getId()))
            .thenReturn(new ArrayList<>(List.of(list)));
    }

    private void finalReport(String status) {
        InternshipReport report = InternshipReport.builder().status(status)
            .document(doc(DocumentType.REPORT, "APPROVED", OffsetDateTime.now())).build();
        when(reportRepository.findByPlacementIdAndReportType(placement.getId(), "FINAL")).thenReturn(Optional.of(report));
    }

    @Test
    void completeOnlyWhenEveryMandatoryItemIsApproved() {
        OffsetDateTime now = OffsetDateTime.now();
        docs(doc(DocumentType.ACCEPTANCE_LETTER, "APPROVED", now),
            doc(DocumentType.AGREEMENT, "APPROVED", now),
            doc(DocumentType.EVIDENCE, "APPROVED", now),
            doc(DocumentType.EXTERNAL_EVALUATION, "APPROVED", now));
        finalReport("APPROVED");

        DossierSnapshot snapshot = inspector.inspect(placement);

        assertThat(snapshot.requiredCount()).isEqualTo(5);
        assertThat(snapshot.approvedCount()).isEqualTo(5);
        assertThat(snapshot.isComplete()).isTrue();
        assertThat(snapshot.problems()).isEmpty();
    }

    @Test
    void reportsMissingPendingAndRevisionProblems() {
        OffsetDateTime now = OffsetDateTime.now();
        docs(doc(DocumentType.ACCEPTANCE_LETTER, "APPROVED", now),
            doc(DocumentType.AGREEMENT, "PENDING", now),
            doc(DocumentType.EVIDENCE, "REVISION_REQUIRED", now));
        finalReport("SUBMITTED");

        DossierSnapshot snapshot = inspector.inspect(placement);

        assertThat(snapshot.isComplete()).isFalse();
        assertThat(snapshot.approvedCount()).isEqualTo(1);
        assertThat(snapshot.submittedCount()).isEqualTo(4);
        assertThat(snapshot.problems())
            .containsExactly("M01 chờ GVHD duyệt", "M02 cần nộp lại", "M03 chưa nộp", "M05 chờ GVHD duyệt");
        assertThatThrownBy(() -> inspector.requireComplete(placement))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("M03 chưa nộp");
    }

    @Test
    void reUploadAfterRevisionUsesLatestFile() {
        OffsetDateTime now = OffsetDateTime.now();
        Document old = doc(DocumentType.AGREEMENT, "REVISION_REQUIRED", now.minusDays(2));
        Document fresh = doc(DocumentType.AGREEMENT, "PENDING", now);
        docs(old, fresh);

        DossierEntry m01 = inspector.inspect(placement).entry(DossierItem.M01).orElseThrow();

        assertThat(m01.document()).isSameAs(fresh);
        assertThat(m01.status()).isEqualTo(DossierEntry.PENDING);
    }

    @Test
    void form04IsApprovedWhenOfficeReceivedPaperAcceptanceForm() {
        docs();
        User office = User.builder().fullName("Văn phòng Khoa").build();
        StudentFoundApplication form = StudentFoundApplication.builder()
            .status(AcceptanceFormStatus.RECEIVED).receivedAt(OffsetDateTime.now()).receivedBy(office).build();
        placement.setStudentFoundApplication(form);

        DossierEntry form04 = inspector.inspect(placement).entry(DossierItem.FORM_04).orElseThrow();

        assertThat(form04.isApproved()).isTrue();
        assertThat(form04.approvedByOffice()).isTrue();
        assertThat(form04.reviewerName()).isEqualTo("Văn phòng Khoa");
    }

    @Test
    void midtermReportFileDoesNotCountAsM05() {
        OffsetDateTime now = OffsetDateTime.now();
        docs(doc(DocumentType.REPORT, "PENDING", now));
        when(reportRepository.findByPlacementIdAndReportType(placement.getId(), "FINAL")).thenReturn(Optional.empty());

        DossierEntry m05 = inspector.inspect(placement).entries().stream()
            .filter(e -> e.item() == DossierItem.M05).findFirst().orElseThrow();

        assertThat(m05.status()).isEqualTo(DossierEntry.MISSING);
        assertThat(inspector.inspect(placement).problems()).contains("M05 chưa nộp");
    }

    @Test
    void noPlacementMeansEverythingMissing() {
        DossierSnapshot snapshot = inspector.inspect(null);

        assertThat(snapshot.submittedCount()).isZero();
        assertThat(snapshot.isComplete()).isFalse();
        assertThat(snapshot.entries()).hasSize(DossierItem.values().length);
    }
}
