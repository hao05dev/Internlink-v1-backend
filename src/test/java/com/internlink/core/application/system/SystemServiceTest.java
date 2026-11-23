package com.internlink.core.application.system;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.internlink.core.application.student.impl.StudentProfileServiceImpl;
import com.internlink.core.application.system.impl.DocumentServiceImpl;
import com.internlink.core.application.system.impl.NotificationServiceImpl;
import com.internlink.core.application.system.notification.EmailOutboxService;
import com.internlink.core.application.system.upload.DocumentAccessPolicy;
import com.internlink.core.application.system.upload.DocumentContentValidator;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.domain.system.Document;
import com.internlink.core.domain.system.Notification;
import com.internlink.core.infrastructure.integration.storage.ClamAvScanner;
import com.internlink.core.infrastructure.integration.storage.StorageService;
import com.internlink.core.infrastructure.integration.storage.StorageServiceRouter;
import com.internlink.core.infrastructure.integration.storage.StoredFileInfo;
import com.internlink.core.shared.enums.StorageProvider;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.student.dto.request.StudentProfileRequest;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class SystemServiceTest {

    @Mock private JpaStudentProfileRepository studentProfileRepository;
    @Mock private JpaUserRepository userRepository;
    @Mock private JpaAcademicProgramRepository programRepository;
    @Mock private com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository rosterRepository;
    @Mock private JpaNotificationRepository notificationRepository;
    @Mock private JpaDocumentRepository documentRepository;
    @Mock private StorageServiceRouter storageServiceRouter;
    @Mock private SecurityGuard securityGuard;

    @Mock private JpaJobApplicationRepository applicationRepository;
    @Mock private JpaStudentFoundApplicationRepository studentFoundApplicationRepository;
    @Mock private JpaLearningAgreementRepository agreementRepository;
    @Mock private JpaInternshipPlacementRepository placementRepository;
    @Mock private JpaPlacementTaskRepository taskRepository;
    @Mock private JpaWeeklyLogbookRepository logbookRepository;
    @Mock private JpaRubricEvaluationRepository rubricRepository;
    @Mock private JpaInternshipCaseRepository caseRepository;

    private StudentProfileServiceImpl profileService;
    private NotificationServiceImpl notificationService;
    private DocumentServiceImpl documentService;

    @BeforeEach
    void setUp() {
        profileService = new StudentProfileServiceImpl(studentProfileRepository, userRepository, programRepository, rosterRepository, documentRepository);
        notificationService = new NotificationServiceImpl(notificationRepository, userRepository, mock(EmailOutboxService.class));

        DocumentAccessPolicy accessPolicy = new DocumentAccessPolicy(
                userRepository, securityGuard, applicationRepository, studentFoundApplicationRepository,
                agreementRepository, placementRepository, taskRepository, logbookRepository,
                rubricRepository, caseRepository);

        documentService = new DocumentServiceImpl(documentRepository, mock(JpaDocumentCryptoEnvelopeRepository.class), storageServiceRouter, accessPolicy, new DocumentContentValidator(), mock(ClamAvScanner.class), mock(com.internlink.core.application.system.upload.DocumentEncryptionPolicy.class), mock(com.internlink.core.application.system.storage.EncryptedDocumentContentService.class));

        lenient().when(studentProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void studentProfileRejectsDuplicateStudentCode() {
        User student = user(UserRole.STUDENT);
        AcademicProgram program = program();
        StudentProfile existing = StudentProfile.builder()
                .userId(UUID.randomUUID()).user(user(UserRole.STUDENT)).program(program).studentCode("B2012345").build();
        when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(programRepository.findById(program.getId())).thenReturn(Optional.of(program));
        when(studentProfileRepository.findByStudentCode("B2012345")).thenReturn(Optional.of(existing));

        StudentProfileRequest request = StudentProfileRequest.builder().programId(program.getId()).studentCode("b2012345").build();

        assertThatThrownBy(() -> profileService.createOrUpdateProfile(student.getId(), request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void notificationCanOnlyBeReadByRecipient() {
        User recipient = user(UserRole.STUDENT);
        Notification notification = Notification.builder()
                .recipient(recipient).notificationType("INFO").title("Title").message("Message").build();
        notification.setId(UUID.randomUUID());
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(notification.getId(), UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void documentRegistrationRejectsNegativeSizeBeforePersistence() {
        User owner = user(UserRole.STUDENT);
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(owner));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> documentService.registerDocument(
                owner.getId(), ContextType.PROFILE, UUID.randomUUID(), DocumentType.CV,
                "cv.pdf", "application/pdf", -1L, "https://file"))
                .isInstanceOf(BadRequestException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void unrelatedLecturerCannotReadStudentsCv() {
        User owner = user(UserRole.STUDENT);
        User lecturer = user(UserRole.LECTURER);
        Document cv = Document.builder()
                .owner(owner).contextType(ContextType.PROFILE).contextId(owner.getId()).documentType(DocumentType.CV).build();
        cv.setId(UUID.randomUUID());
        when(documentRepository.findById(cv.getId())).thenReturn(Optional.of(cv));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(lecturer));
        when(userRepository.findById(lecturer.getId())).thenReturn(Optional.of(lecturer));

        assertThatThrownBy(() -> documentService.downloadDocument(cv.getId(), lecturer.getId()))
                .isInstanceOf(ForbiddenException.class);
        verify(storageServiceRouter, never()).getStorageService(any());
    }

    @Test
    void failedDocumentPersistenceRemovesUploadedObjectAfterRollback() {
        User owner = user(UserRole.STUDENT);
        StorageService storage = mock(StorageService.class);
        StoredFileInfo stored = StoredFileInfo.builder()
                .provider(StorageProvider.R2).providerFileId("internlink/profile/test.pdf")
                .originalName("cv.pdf").mimeType("application/pdf").sizeBytes(3L).build();
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(owner));
        when(storageServiceRouter.getActiveStorageService()).thenReturn(storage);
        when(storage.store(any(), any())).thenReturn(stored);
        when(documentRepository.save(any())).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if ("ACTIVE".equals(d.getStatus())) {
                throw new IllegalStateException("database unavailable");
            }
            return d;
        });

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> documentService.uploadDocument(
                    owner.getId(), ContextType.PROFILE, owner.getId(), DocumentType.CV,
                    new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.7 fixture".getBytes())))
                    .isInstanceOf(IllegalStateException.class);
            verify(storage).delete(stored.getProviderFileId());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void placementReportUploadRejectsOversizeAndWrongFormat() {
        User student = user(UserRole.STUDENT);
        com.internlink.core.domain.placement.InternshipPlacement placement =
                com.internlink.core.domain.placement.InternshipPlacement.builder().student(student).build();
        placement.setId(UUID.randomUUID());
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(student));
        when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(placementRepository.findById(placement.getId())).thenReturn(Optional.of(placement));

        org.springframework.web.multipart.MultipartFile oversize =
                mock(org.springframework.web.multipart.MultipartFile.class);
        when(oversize.isEmpty()).thenReturn(false);
        when(oversize.getSize()).thenReturn(20L * 1024 * 1024 + 1);
        assertThatThrownBy(() -> documentService.uploadDocument(
                student.getId(), ContextType.PLACEMENT, placement.getId(), DocumentType.REPORT, oversize))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("20 MiB");

        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "bao-cao.pdf", "application/pdf", "MZ not really a pdf".getBytes());
        assertThatThrownBy(() -> documentService.uploadDocument(
                student.getId(), ContextType.PLACEMENT, placement.getId(), DocumentType.REPORT, fakePdf))
                .isInstanceOf(BadRequestException.class);

        MockMultipartFile exe = new MockMultipartFile(
                "file", "bao-cao.exe", "application/octet-stream", "MZ".getBytes());
        assertThatThrownBy(() -> documentService.uploadDocument(
                student.getId(), ContextType.PLACEMENT, placement.getId(), DocumentType.REPORT, exe))
                .isInstanceOf(BadRequestException.class);
        verify(storageServiceRouter, never()).getActiveStorageService();
        verify(documentRepository, never()).save(any());
    }

    private User user(UserRole role) {
        User value = User.builder().email(UUID.randomUUID() + "@example.com").passwordHash("hash").fullName("User").role(role).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private AcademicProgram program() {
        Department department = Department.builder().code(UUID.randomUUID().toString()).name("CNTT").contactEmail("it@example.com").build();
        department.setId(UUID.randomUUID());
        AcademicProgram value = AcademicProgram.builder().department(department).code("SE").name("Software Engineering").build();
        value.setId(UUID.randomUUID());
        return value;
    }
}

