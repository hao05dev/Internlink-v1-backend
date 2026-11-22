package com.internlink.core.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.evaluation.*;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.evaluation.dto.request.ComponentScoreRequest;
import com.internlink.core.shared.enums.*;
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
class ComponentScoreServiceTest {
    @Mock JpaAssessmentComponentScoreRepository scores;
    @Mock JpaInternshipPlacementRepository placements;
    @Mock JpaDocumentRepository documents;
    @Mock JpaUserRepository users;
    @Mock SecurityGuard security;

    @Test
    void lecturerVerifiesOfflineHostFormWithoutCreatingMentorAccount() {
        var service =
                new ComponentScoreService(
                        scores,
                        placements,
                        documents,
                        users,
                        security,
                        mock(com.internlink.core.application.placement.dossier.DossierInspector.class),
                        mock(
                                com.internlink.core.application.system.notification
                                        .PlacementWorkflowNotificationHelper.class));
        User lecturer = User.builder().role(UserRole.LECTURER).build();
        lecturer.setId(UUID.randomUUID());
        User student = User.builder().role(UserRole.STUDENT).build();
        student.setId(UUID.randomUUID());
        AssessmentScheme scheme =
                AssessmentScheme.builder()
                        .status("APPROVED")
                        .components(
                                List.of(
                                        Map.of(
                                                "code",
                                                "HOST",
                                                "name",
                                                "Nơi thực tập",
                                                "assessorRole",
                                                "COMPANY_MENTOR",
                                                "weight",
                                                "1.0")))
                        .build();
        InternshipPlacement placement =
                InternshipPlacement.builder()
                        .source("STUDENT_FOUND")
                        .student(student)
                        .lecturer(lecturer)
                        .assessmentScheme(scheme)
                        .status(PlacementStatus.ACTIVE)
                        .build();
        placement.setId(UUID.randomUUID());
        Document signedForm =
                Document.builder()
                        .owner(student)
                        .contextType(ContextType.PLACEMENT)
                        .contextId(placement.getId())
                        .documentType(DocumentType.EXTERNAL_EVALUATION)
                        .status("ACTIVE")
                        .build();
        signedForm.setId(UUID.randomUUID());
        when(placements.findById(placement.getId())).thenReturn(Optional.of(placement));
        when(documents.findById(signedForm.getId())).thenReturn(Optional.of(signedForm));
        when(security.currentUser()).thenReturn(CustomUserDetail.create(lecturer));
        when(users.findById(lecturer.getId())).thenReturn(Optional.of(lecturer));
        when(scores.save(any()))
                .thenAnswer(
                        invocation -> {
                            AssessmentComponentScore score = invocation.getArgument(0);
                            if (score.getId() == null) score.setId(UUID.randomUUID());
                            return score;
                        });
        var submitted =
                service.submit(
                        new ComponentScoreRequest(
                                placement.getId(),
                                "HOST",
                                new BigDecimal("8.0"),
                                Map.of(),
                                signedForm.getId(),
                                "OFFLINE"));
        assertThat(submitted.status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(placement.getMentor()).isNull();
        var record =
                AssessmentComponentScore.builder()
                        .placement(placement)
                        .componentCode("HOST")
                        .score(new BigDecimal("8.0"))
                        .criteriaScores(Map.of())
                        .source("OFFLINE")
                        .evidenceDocument(signedForm)
                        .submittedBy(lecturer)
                        .status("PENDING_VERIFICATION")
                        .build();
        record.setId(submitted.id());
        when(scores.findByPlacementIdAndComponentCode(placement.getId(), "HOST"))
                .thenReturn(Optional.of(record));
        var corrected =
                service.submit(
                        new ComponentScoreRequest(
                                placement.getId(),
                                "HOST",
                                new BigDecimal("8.5"),
                                Map.of(),
                                signedForm.getId(),
                                "OFFLINE"));
        assertThat(corrected.score()).isEqualByComparingTo("8.5");
        assertThat(corrected.status()).isEqualTo("PENDING_VERIFICATION");
        when(scores.findById(record.getId())).thenReturn(Optional.of(record));
        assertThat(service.verify(record.getId()).status()).isEqualTo("VERIFIED");
    }
}
