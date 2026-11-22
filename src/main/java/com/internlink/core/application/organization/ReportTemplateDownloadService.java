package com.internlink.core.application.organization;

import com.internlink.core.domain.organization.ReportTemplateVersion;
import com.internlink.core.infrastructure.integration.storage.StorageService;
import com.internlink.core.infrastructure.integration.storage.StorageServiceRouter;
import com.internlink.core.infrastructure.persistence.jpa.JpaReportTemplateVersionRepository;
import com.internlink.core.presentation.organization.dto.response.ReportTemplateResponse;
import com.internlink.core.shared.enums.ReportTemplateType;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Tải file biểu mẫu: theo id, hoặc theo loại mẫu cho đúng khoa/ngành của sinh viên. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportTemplateDownloadService {

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final JpaReportTemplateVersionRepository templateRepository;
    private final StorageServiceRouter storageServiceRouter;
    private final SampleTemplateDocumentGenerator sampleGenerator;
    private final TemplateSelector templateSelector;

    @Transactional(readOnly = true)
    public TemplateDownload loadTemplateDownload(UUID templateId) {
        ReportTemplateVersion entity = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportTemplateVersion", "id", templateId));
        return toDownload(entity);
    }

    /** M04 cho sinh viên: lấy theo khoa/ngành của sinh viên, programCode chỉ dùng khi chưa biết ngành. */
    @Transactional(readOnly = true)
    public ReportTemplateResponse findEvaluationTemplate(UUID termId, String programCode, TemplateAudience audience) {
        TemplateAudience scoped = new TemplateAudience(
            termId != null ? termId : audience.termId(),
            audience.departmentId(),
            audience.programCode(),
            audience.internshipCredits()
        ).withProgramCodeIfMissing(programCode);
        return templateSelector.select(ReportTemplateType.M04, scoped)
            .map(ReportTemplateResponse::fromEntity)
            .orElse(null);
    }

    /** Tải mẫu (.doc/.docx gốc) phù hợp với sinh viên; không có thì sinh file mẫu chuẩn. */
    @Transactional(readOnly = true)
    public TemplateDownload loadForAudience(String templateCode, String programCode, TemplateAudience audience) {
        TemplateAudience scoped = audience.withProgramCodeIfMissing(programCode);
        ReportTemplateType type = TemplateSelector.effectiveType(resolveReportTemplateType(templateCode), scoped);
        Optional<ReportTemplateVersion> selected = templateSelector.select(type, scoped);
        if (selected.isPresent()) {
            return toDownload(selected.get());
        }
        String code = sampleProgramCode(scoped);
        Resource resource = sampleGenerator.generateSample(null, type, code);
        return new TemplateDownload(resource, sampleGenerator.resolveDefaultFileName(type, code), "application/msword");
    }

    /** Bản .docx để xem trước trên trình duyệt; mẫu đã treo không phải .docx thì sinh bản xem trước. */
    @Transactional(readOnly = true)
    public TemplateDownload loadPreviewForAudience(String templateCode, String programCode, TemplateAudience audience) {
        TemplateAudience scoped = audience.withProgramCodeIfMissing(programCode);
        ReportTemplateType type = TemplateSelector.effectiveType(resolveReportTemplateType(templateCode), scoped);
        Optional<ReportTemplateVersion> selected = templateSelector.select(type, scoped)
            .filter(t -> t.getFileUrl() != null && t.getFileUrl().toLowerCase().endsWith(".docx"));
        if (selected.isPresent()) {
            return toDownload(selected.get());
        }
        String code = sampleProgramCode(scoped);
        Resource resource = sampleGenerator.generateSampleDocx(null, type, code);
        return new TemplateDownload(resource, sampleGenerator.resolveDefaultDocxFileName(type, code), DOCX_MIME);
    }

    public TemplateDownload generateFallbackDownload(ReportTemplateType reportType, String programCode) {
        Resource resource = sampleGenerator.generateSample(null, reportType, programCode);
        String fileName = sampleGenerator.resolveDefaultFileName(reportType, programCode);
        return new TemplateDownload(resource, fileName, "application/msword");
    }

    public static ReportTemplateType resolveReportTemplateType(String templateCode) {
        if (templateCode == null) return ReportTemplateType.OTHER;
        String code = templateCode.trim().toUpperCase();
        try {
            return ReportTemplateType.valueOf(code);
        } catch (IllegalArgumentException ex) {
            return switch (code) {
                case "04", "FORM_04", "FORM_04_8W" -> ReportTemplateType.FORM_04_RECEPTION_8W;
                case "04_12W", "FORM_04_12W" -> ReportTemplateType.FORM_04_RECEPTION_12W;
                case "M-TT-01", "M01_12W" -> ReportTemplateType.M01;
                case "M-TT-02" -> ReportTemplateType.M02;
                case "M-TT-03" -> ReportTemplateType.M03;
                // "M-TT-04" không gắn khoa: để TemplateSelector chọn bản đúng khoa của sinh viên.
                case "M-TT-04" -> ReportTemplateType.M04;
                case "M-TT-04-KHMT" -> ReportTemplateType.M04_LECTURER_KHMT;
                case "M-TT-04-MMT" -> ReportTemplateType.M04_LECTURER_MMT;
                case "M-TT-05" -> ReportTemplateType.M05;
                default -> ReportTemplateType.OTHER;
            };
        }
    }

    static String extractStorageKey(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return "";
        if (fileUrl.startsWith("r2://")) {
            int slashAfterBucket = fileUrl.indexOf('/', 5);
            return slashAfterBucket != -1 ? fileUrl.substring(slashAfterBucket + 1) : fileUrl;
        }
        return fileUrl;
    }

    private TemplateDownload toDownload(ReportTemplateVersion entity) {
        String key = extractStorageKey(entity.getFileUrl());
        Resource resource = null;
        if (!key.isBlank() && !key.startsWith("/api/v1/storage/")) {
            try {
                StorageService storage = storageServiceRouter.getActiveStorageService();
                resource = storage.loadAsResource(key);
            } catch (Exception ex) {
                log.warn("Storage template fetch failed for id={}, key={}. Falling back to sample generator: {}",
                    entity.getId(), key, ex.getMessage());
            }
        }
        if (resource == null) {
            resource = sampleGenerator.loadOfficialResource(entity.getFileName());
        }
        if (resource == null) {
            resource = sampleGenerator.generateSample(entity.getTitle(), entity.getReportType(), entity.getProgramCode());
        }
        return new TemplateDownload(resource, entity.getFileName(), entity.getMimeType());
    }

    private static String sampleProgramCode(TemplateAudience audience) {
        return audience.programCode() != null ? audience.programCode() : "DEFAULT";
    }
}
