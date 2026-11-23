package com.internlink.core.application.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.internlink.core.application.system.notification.PlacementWorkflowNotificationHelper;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.evaluation.AssessmentScheme;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.InternshipReport;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.JpaDocumentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipReportRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Nộp báo cáo thực tập: trễ hạn, khóa sau khi duyệt và quyền xem theo placement. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternshipReportServiceTest {

    @Mock private JpaInternshipReportRepository reports;
    @Mock private JpaInternshipPlacementRepository placements;
    @Mock private JpaDocumentRepository documents;
    @Mock private JpaUserRepository users;
    @Mock private SecurityGuard security;
    @Mock private PlacementWorkflowNotificationHelper notifications;

    private InternshipReportService service;
    private User student;
    private InternshipPlacement placement;
    private AssessmentScheme scheme;
    private Document document;

    @BeforeEach
    void setUp() {
        service = new InternshipReportService(reports, placements, documents, users, security, notifications);
        student = user(UserRole.STUDENT);
        scheme = AssessmentScheme.builder().build();
        placement = InternshipPlacement.builder()
                .student(student)
                .lecturer(user(UserRole.LECTURER))
                .status(PlacementStatus.ACTIVE)
                .assessmentScheme(scheme)
                .build();
        placement.setId(UUID.randomUUID());
        document = Document.builder()
                .owner(student)
                .contextType(ContextType.PLACEMENT)
                .contextId(placement.getId())
                .documentType(DocumentType.REPORT)
                .status("ACTIVE")
                .originalName("bao-cao.pdf")
                .sizeBytes(1024L)
                .build();
        document.setId(UUID.randomUUID());
        when(placements.findById(placement.getId())).thenReturn(Optional.of(placement));
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        when(reports.findByPlacementIdAndReportType(any(), any())).thenReturn(Optional.empty());
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void finalReportSubmittedAfterDueDateIsMarkedLate() {
        scheme.setFinalReportDueAt(OffsetDateTime.now().minusDays(1));
        actAs(student);

        var response = service.submit(placement.getId(), "FINAL", document.getId());

        assertThat(response.status()).isEqualTo("SUBMITTED");
        assertThat(response.wasLate()).isTrue();
    }

    @Test
    void finalReportSubmittedBeforeDueDateIsOnTime() {
        scheme.setFinalReportDueAt(OffsetDateTime.now().plusDays(1));
        actAs(student);

        assertThat(service.submit(placement.getId(), "FINAL", document.getId()).wasLate()).isFalse();
    }

    @Test
    void approvedReportCannotBeResubmitted() {
        InternshipReport approved = InternshipReport.builder()
                .placement(placement).reportType("FINAL").document(document)
                .status("APPROVED").wasLate(false).submittedAt(OffsetDateTime.now()).build();
        approved.setId(UUID.randomUUID());
        when(reports.findByPlacementIdAndReportType(placement.getId(), "FINAL")).thenReturn(Optional.of(approved));
        actAs(student);

        assertThatThrownBy(() -> service.submit(placement.getId(), "FINAL", document.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã được GVHD duyệt");
        verify(reports, never()).save(any());
    }

    @Test
    void anotherStudentCannotReadReportsOfPlacement() {
        actAs(user(UserRole.STUDENT));

        assertThatThrownBy(() -> service.byPlacement(placement.getId()))
                .isInstanceOf(ForbiddenException.class);
        verify(reports, never()).findByPlacementId(any());
    }

    private void actAs(User actor) {
        CustomUserDetail detail = mock(CustomUserDetail.class);
        lenient().when(detail.getId()).thenReturn(actor.getId());
        lenient().when(security.currentUser()).thenReturn(detail);
        lenient().when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
    }

    private static User user(UserRole role) {
        User value = User.builder()
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .fullName("User " + role)
                .role(role)
                .build();
        value.setId(UUID.randomUUID());
        return value;
    }
}
