package com.internlink.core.application.placement.impl;

import com.internlink.core.application.placement.ReportDeadlines;
import com.internlink.core.application.placement.StudentDossierService;
import com.internlink.core.application.placement.dossier.DossierEntry;
import com.internlink.core.application.placement.dossier.DossierInspector;
import com.internlink.core.application.placement.dossier.DossierItem;
import com.internlink.core.application.placement.dossier.DossierResponseMapper;
import com.internlink.core.application.system.notification.PlacementWorkflowNotificationHelper;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.InternshipReport;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.integration.storage.StorageService;
import com.internlink.core.infrastructure.integration.storage.StorageServiceRouter;
import com.internlink.core.infrastructure.integration.storage.StoredFileInfo;
import com.internlink.core.infrastructure.persistence.jpa.JpaDocumentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipReportRepository;
import com.internlink.core.presentation.placement.dto.response.StudentDossierResponse;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentDossierServiceImpl implements StudentDossierService {

    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaDocumentRepository documentRepository;
    private final JpaInternshipReportRepository reportRepository;
    private final StorageServiceRouter storageServiceRouter;
    private final DossierInspector dossierInspector;
    private final DossierResponseMapper responseMapper;
    private final PlacementWorkflowNotificationHelper notifications;

    @Override
    @Transactional(readOnly = true)
    public StudentDossierResponse getStudentDossier(User student) {
        InternshipPlacement placement = currentPlacement(student);
        return responseMapper.toResponse(placement, dossierInspector.inspect(placement));
    }

    private InternshipPlacement currentPlacement(User student) {
        List<InternshipPlacement> placements = placementRepository.findByStudentId(student.getId());
        return placements.stream()
            .filter(p -> p.getStatus() == PlacementStatus.ACTIVE)
            .findFirst()
            .orElse(placements.isEmpty() ? null : placements.get(0));
    }

    @Override
    @Transactional
    public StudentDossierResponse uploadDossierDocument(UUID placementId, String templateCode, MultipartFile file, User student) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tin tải lên không được để trống");
        }
        InternshipPlacement placement;
        if (placementId == null) {
            placement = currentPlacement(student);
            if (placement == null) {
                throw new BadRequestException("Không tìm thấy đợt thực tập của sinh viên.");
            }
        } else {
            placement = placementRepository.findById(placementId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipPlacement", "id", placementId));
        }

        if (!placement.getStudent().getId().equals(student.getId())) {
            throw new BadRequestException("Bạn không có quyền nộp hồ sơ cho đợt thực tập này");
        }

        if (placement.getTerm() != null) {
            TermGuard.requireNotClosed(placement.getTerm());
        }

        if (placement.getStatus() == PlacementStatus.TERMINATED) {
            throw new BadRequestException("Đợt thực tập đã bị chấm dứt. Toàn bộ hồ sơ đã chuyển sang chế độ lưu trữ khóa.");
        }

        OffsetDateTime deadline = ReportDeadlines.due(placement, "FINAL");
        if (deadline != null && OffsetDateTime.now().isAfter(deadline)) {
            throw new BadRequestException("Đã hết thời hạn nộp hồ sơ thực tập quy định (" + deadline + "). Hệ thống đã khóa nộp hồ sơ.");
        }

        DossierItem item = DossierItem.ofTemplateCode(templateCode)
            .orElseThrow(() -> new BadRequestException("Mã biểu mẫu hồ sơ không hợp lệ: " + templateCode));
        boolean alreadyApproved = dossierInspector.inspect(placement).entry(item)
            .map(DossierEntry::isApproved).orElse(false);
        if (alreadyApproved) {
            throw new BadRequestException(item.shortLabel() + " đã được duyệt, không cần nộp lại.");
        }
        DocumentType docType = item.documentType();
        String subFolder = "dossier/" + student.getId();

        StorageService storage = storageServiceRouter.getActiveStorageService();
        StoredFileInfo fileInfo;
        try {
            fileInfo = storage.store(file, subFolder);
        } catch (Exception ex) {
            log.error("Lỗi khi lưu tệp hồ sơ sinh viên: {}", ex.getMessage(), ex);
            throw new BadRequestException("Không thể lưu trữ tệp tin: " + ex.getMessage());
        }
        deleteStoredFileOnRollback(storage, fileInfo);

        Document doc = Document.builder()
            .owner(student)
            .contextType(ContextType.PLACEMENT)
            .contextId(placement.getId())
            .documentType(docType)
            .storageProvider(fileInfo.getProvider() != null ? fileInfo.getProvider() : storage.getProviderType())
            .externalUrl(fileInfo.getStoragePathOrUrl())
            .providerFileId(fileInfo.getProviderFileId())
            .originalName(file.getOriginalFilename())
            .sizeBytes(file.getSize())
            .mimeType(file.getContentType())
            .checksum(fileInfo.getChecksum())
            .status("ACTIVE")
            .build();

        // BaseEntity.version khởi tạo = 0 nên Spring Data dùng merge(): phải dùng bản trả về (managed),
        // không dùng `doc` gốc (vẫn transient) khi gắn vào InternshipReport.
        Document saved = documentRepository.save(doc);

        if (docType == DocumentType.REPORT) {
            syncInternshipReport(placement, saved);
        }
        notifications.dossierSubmitted(placement, student.getId(), item.shortLabel());

        return getStudentDossier(student);
    }

    private void syncInternshipReport(InternshipPlacement placement, Document doc) {
        // Không nuốt lỗi ở đây: exception qua repository đã đánh dấu rollback-only,
        // nuốt lỗi chỉ khiến commit thất bại với UnexpectedRollbackException (500 khó hiểu).
        var report = reportRepository.findByPlacementIdAndReportType(placement.getId(), "FINAL")
            .orElseGet(() -> InternshipReport.builder()
                .placement(placement)
                .reportType("FINAL")
                .wasLate(false)
                .build());
        report.setDocument(doc);
        report.setSubmittedAt(OffsetDateTime.now());
        report.setStatus("SUBMITTED");
        reportRepository.save(report);
    }

    /** Upload lên R2/Drive xảy ra trước khi ghi DB; nếu giao dịch rollback thì xóa tệp để không bị mồ côi. */
    private void deleteStoredFileOnRollback(StorageService storage, StoredFileInfo fileInfo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || fileInfo.getProviderFileId() == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) return;
                try {
                    storage.delete(fileInfo.getProviderFileId());
                } catch (RuntimeException ex) {
                    log.warn("Không xóa được tệp mồ côi {} sau rollback: {}", fileInfo.getProviderFileId(), ex.getMessage());
                }
            }
        });
    }
}
