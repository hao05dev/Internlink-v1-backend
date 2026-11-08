package com.internlink.core.application.placement.impl;

import com.internlink.core.application.placement.ReportDeadlines;
import com.internlink.core.application.placement.StudentDossierService;
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
import com.internlink.core.infrastructure.persistence.jpa.JpaLearningAgreementRepository;
import com.internlink.core.presentation.placement.dto.response.StudentDossierResponse;
import com.internlink.core.presentation.placement.dto.response.StudentDossierResponse.StudentDossierItemDto;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.TermStatus;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final JpaLearningAgreementRepository agreementRepository;
    private final StorageServiceRouter storageServiceRouter;

    @Override
    @Transactional(readOnly = true)
    public StudentDossierResponse getStudentDossier(User student) {
        List<InternshipPlacement> placements = placementRepository.findByStudentId(student.getId());
        InternshipPlacement placement = placements.stream()
            .filter(p -> "ACTIVE".equals(String.valueOf(p.getStatus())))
            .findFirst()
            .orElse(placements.isEmpty() ? null : placements.get(0));

        UUID placementId = placement != null ? placement.getId() : null;
        String companyName = placement != null && placement.getCompany() != null
            ? placement.getCompany().getCompanyName()
            : "Đơn vị tiếp nhận";
        String placementTitle = placement != null
            ? "Thực tập tại " + companyName
            : "Học phần Thực tập tốt nghiệp";

        List<Document> docs = (placementId != null)
            ? documentRepository.findByContextTypeAndContextId(ContextType.PLACEMENT, placementId)
            : List.of();

        List<StudentDossierItemDto> items = new ArrayList<>();
        items.add(buildItem("FORM_04", "Phiếu tiếp nhận sinh viên thực tập (Mẫu 04)", "Doanh nghiệp ký & đóng dấu xác nhận tiếp nhận sinh viên", "ONBOARDING", "Doanh nghiệp tiếp nhận", true, findDoc(docs, DocumentType.ACCEPTANCE_LETTER)));
        items.add(buildItem("M01", "Phiếu giao việc thực tập doanh nghiệp (M-TT-01)", "Mentor phân công nhiệm vụ, đề tài và kế hoạch làm việc", "ONBOARDING", "Mentor doanh nghiệp", true, findDoc(docs, DocumentType.AGREEMENT)));
        items.add(buildItem("M02", "Phiếu theo dõi tiến độ thực tập (M-TT-02)", "Ghi chép và xác nhận tiến độ thực tập định kỳ", "IN_PROGRESS", "Sinh viên & Mentor", true, findDoc(docs, DocumentType.EVIDENCE)));
        items.add(buildItem("M03", "Phiếu đánh giá của cơ quan tiếp nhận (M-TT-03)", "Đơn vị tiếp nhận chấm điểm thực tập và đóng dấu xác nhận", "CLOSING", "Lãnh đạo DN & Mentor", true, findDoc(docs, DocumentType.EXTERNAL_EVALUATION)));
        items.add(buildItem("M05", "Đề cương & Báo cáo tổng kết thực tập (M-TT-05)", "Báo cáo tốt nghiệp hoàn chỉnh nộp cho Khoa và GVHD", "CLOSING", "Sinh viên", true, findDoc(docs, DocumentType.REPORT)));
        items.add(buildItem("M04", "Phiếu đánh giá dành cho GVHD (M-TT-04)", "Tiêu chí và thang điểm đánh giá của Giảng viên hướng dẫn (Tham khảo)", "CLOSING", "Giảng viên hướng dẫn", false, null));

        int totalRequired = (int) items.stream().filter(StudentDossierItemDto::isMandatory).count();
        int submittedCount = (int) items.stream().filter(i -> i.isMandatory() && i.isSubmitted()).count();
        int missingCount = totalRequired - submittedCount;
        int completionPercent = totalRequired > 0 ? (int) Math.round((submittedCount * 100.0) / totalRequired) : 0;

        boolean isTermClosed = placement != null && placement.getTerm() != null && placement.getTerm().getStatus() == TermStatus.CLOSED;
        boolean isPlacementFinished = placement != null && placement.getStatus() != PlacementStatus.ACTIVE;
        OffsetDateTime deadline = placement != null ? ReportDeadlines.due(placement, "FINAL") : null;
        boolean isOverdue = deadline != null && OffsetDateTime.now().isAfter(deadline);

        boolean isLocked = isTermClosed || isPlacementFinished || isOverdue;
        String lockReason = null;
        if (isTermClosed) {
            lockReason = "Học kỳ thực tập đã chính thức kết thúc (CLOSED). Toàn bộ hồ sơ đã khóa lưu trữ học vụ.";
        } else if (isPlacementFinished) {
            lockReason = "Đợt thực tập của bạn đã hoàn tất/kết thúc (" + placement.getStatus() + "). Toàn bộ hồ sơ đã khóa lưu trữ.";
        } else if (isOverdue) {
            lockReason = "Đã quá thời hạn nộp hồ sơ thực tập quy định. Hệ thống đã khóa nộp hồ sơ.";
        }

        return StudentDossierResponse.builder()
            .placementId(placementId)
            .placementTitle(placementTitle)
            .companyName(companyName)
            .totalRequired(totalRequired)
            .submittedCount(submittedCount)
            .missingCount(missingCount)
            .completionPercentage(completionPercent)
            .isLocked(isLocked)
            .lockReason(lockReason)
            .deadline(deadline)
            .items(items)
            .build();
    }

    @Override
    @Transactional
    public StudentDossierResponse uploadDossierDocument(UUID placementId, String templateCode, MultipartFile file, User student) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tin tải lên không được để trống");
        }
        InternshipPlacement placement = placementRepository.findById(placementId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipPlacement", "id", placementId));

        if (!placement.getStudent().getId().equals(student.getId())) {
            throw new BadRequestException("Bạn không có quyền nộp hồ sơ cho đợt thực tập này");
        }

        if (placement.getTerm() != null) {
            TermGuard.requireNotClosed(placement.getTerm());
        }

        if (placement.getStatus() != PlacementStatus.ACTIVE) {
            throw new BadRequestException("Đợt thực tập đã hoàn tất/kết thúc (" + placement.getStatus() + "). Toàn bộ hồ sơ đã chuyển sang chế độ lưu trữ khóa.");
        }

        OffsetDateTime deadline = ReportDeadlines.due(placement, "FINAL");
        if (deadline != null && OffsetDateTime.now().isAfter(deadline)) {
            throw new BadRequestException("Đã hết thời hạn nộp hồ sơ thực tập quy định (" + deadline + "). Hệ thống đã khóa nộp hồ sơ.");
        }

        DocumentType docType = resolveDocType(templateCode);
        String subFolder = "dossier/" + student.getId();

        try {
            StorageService storage = storageServiceRouter.getActiveStorageService();
            StoredFileInfo fileInfo = storage.store(file, subFolder);

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

            documentRepository.save(doc);

            if (docType == DocumentType.REPORT) {
                syncInternshipReport(placement, doc);
            }
        } catch (Exception ex) {
            log.error("Lỗi khi lưu tệp hồ sơ sinh viên: {}", ex.getMessage(), ex);
            throw new BadRequestException("Không thể lưu trữ tệp tin: " + ex.getMessage());
        }

        return getStudentDossier(student);
    }

    private Document findDoc(List<Document> docs, DocumentType type) {
        return docs.stream()
            .filter(d -> d.getDocumentType() == type && "ACTIVE".equals(d.getStatus()))
            .max(Comparator.comparing(Document::getCreatedAt))
            .orElse(null);
    }

    private StudentDossierItemDto buildItem(String code, String title, String desc, String phase, String signer, boolean mandatory, Document doc) {
        boolean isSubmitted = doc != null;
        String status = !isSubmitted ? "MISSING" : ("REJECTED".equalsIgnoreCase(doc.getStatus()) ? "REVISION_REQUIRED" : "APPROVED");
        String downloadUrl = "/api/v1/organization/templates/official/" + code + "/download";
        String previewUrl = "/api/v1/organization/templates/official/" + code + "/preview";

        return StudentDossierItemDto.builder()
            .templateCode(code)
            .title(title)
            .description(desc)
            .phase(phase)
            .signer(signer)
            .isMandatory(mandatory)
            .isSubmitted(isSubmitted)
            .status(status)
            .documentId(doc != null ? doc.getId() : null)
            .submittedFileName(doc != null ? doc.getOriginalName() : null)
            .submittedFileSizeBytes(doc != null ? doc.getSizeBytes() : null)
            .submittedAt(doc != null ? doc.getCreatedAt() : null)
            .downloadTemplateUrl(downloadUrl)
            .previewTemplateUrl(previewUrl)
            .fileUrl(doc != null ? doc.getExternalUrl() : null)
            .build();
    }

    private DocumentType resolveDocType(String templateCode) {
        return switch (templateCode.trim().toUpperCase()) {
            case "FORM_04", "04", "FORM_04_8W", "FORM_04_12W" -> DocumentType.ACCEPTANCE_LETTER;
            case "M01", "M-TT-01", "M01_12W" -> DocumentType.AGREEMENT;
            case "M02", "M-TT-02", "M02_12W" -> DocumentType.EVIDENCE;
            case "M03", "M-TT-03" -> DocumentType.EXTERNAL_EVALUATION;
            case "M05", "M-TT-05", "FINAL_REPORT" -> DocumentType.REPORT;
            default -> DocumentType.REPORT;
        };
    }

    private void syncInternshipReport(InternshipPlacement placement, Document doc) {
        try {
            var existingReport = reportRepository.findByPlacementIdAndReportType(placement.getId(), "FINAL")
                .orElse(InternshipReport.builder()
                    .placement(placement)
                    .reportType("FINAL")
                    .submittedAt(OffsetDateTime.now())
                    .wasLate(false)
                    .status("PENDING")
                    .build());
            existingReport.setDocument(doc);
            existingReport.setSubmittedAt(OffsetDateTime.now());
            reportRepository.save(existingReport);
        } catch (Exception ex) {
            log.debug("Bỏ qua đồng bộ internship_reports: {}", ex.getMessage());
        }
    }
}
