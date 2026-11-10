package com.internlink.core.application.organization;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.ReportTemplateVersion;
import com.internlink.core.infrastructure.integration.storage.StorageService;
import com.internlink.core.infrastructure.integration.storage.StorageServiceRouter;
import com.internlink.core.infrastructure.integration.storage.StoredFileInfo;
import com.internlink.core.infrastructure.persistence.jpa.JpaAcademicProgramRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaDepartmentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaReportTemplateVersionRepository;
import com.internlink.core.presentation.organization.dto.response.ReportTemplateResponse;
import com.internlink.core.shared.enums.ReportTemplateType;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportTemplateService {

    private final JpaReportTemplateVersionRepository templateRepository;
    private final JpaInternshipTermRepository termRepository;
    private final JpaDepartmentRepository departmentRepository;
    private final JpaAcademicProgramRepository programRepository;
    private final StorageServiceRouter storageServiceRouter;
    private final SampleTemplateDocumentGenerator sampleGenerator;

    @Transactional(readOnly = true)
    public List<ReportTemplateResponse> getTemplates(UUID termId, UUID departmentId, Boolean activeOnly) {
        boolean onlyActive = activeOnly == null || activeOnly;
        List<ReportTemplateVersion> list = (departmentId != null)
            ? (onlyActive
                ? templateRepository.findByTermIdAndDepartmentIdAndIsActiveTrue(termId, departmentId)
                : templateRepository.findByTermIdAndDepartmentIdOrderByCreatedAtDesc(termId, departmentId))
            : (onlyActive
                ? templateRepository.findByTermIdAndIsActiveTrue(termId)
                : templateRepository.findByTermIdOrderByCreatedAtDesc(termId));
        return list.stream().map(ReportTemplateResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public ReportTemplateResponse getEvaluationTemplateForStudent(UUID termId, String programCode) {
        String code = programCode != null ? programCode.trim().toUpperCase() : "DEFAULT";
        Optional<ReportTemplateVersion> template = templateRepository
            .findFirstByTermIdAndReportTypeAndProgramCodeAndIsActiveTrueOrderByVersionNumberDesc(
                termId, ReportTemplateType.M04, code);

        if (template.isEmpty()) {
            template = templateRepository
                .findFirstByTermIdAndReportTypeAndProgramCodeAndIsActiveTrueOrderByVersionNumberDesc(
                    termId, ReportTemplateType.M04, "DEFAULT");
        }
        if (template.isEmpty()) {
            template = templateRepository
                .findFirstByTermIdAndReportTypeAndIsActiveTrueOrderByVersionNumberDesc(
                    termId, ReportTemplateType.M04);
        }
        return template.map(ReportTemplateResponse::fromEntity).orElse(null);
    }

    @Transactional
    public ReportTemplateResponse uploadTemplate(
        UUID termId, UUID departmentId, UUID programId, String programCode,
        ReportTemplateType reportType, String title, String description,
        Boolean isMandatory, MultipartFile file, User uploader
    ) {
        verifyFacultyAdminOrAdmin(uploader);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tin biểu mẫu không được để trống");
        }

        InternshipTerm term = termRepository.findById(termId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        Department department = departmentId != null
            ? departmentRepository.findById(departmentId).orElse(null)
            : uploader.getDepartment();

        AcademicProgram program = null;
        String resolvedProgramCode = programCode != null && !programCode.isBlank()
            ? programCode.trim().toUpperCase() : "DEFAULT";
        if (programId != null) {
            program = programRepository.findById(programId).orElse(null);
            if (program != null && (programCode == null || programCode.isBlank())) {
                resolvedProgramCode = program.getCode();
            }
        }

        int nextVersion = templateRepository
            .findFirstByTermIdAndReportTypeAndProgramCodeOrderByVersionNumberDesc(termId, reportType, resolvedProgramCode)
            .map(t -> t.getVersionNumber() + 1).orElse(1);

        StorageService storage = storageServiceRouter.getActiveStorageService();
        String subFolder = "templates/" + termId + "/" + reportType.name().toLowerCase() + "/" + resolvedProgramCode.toLowerCase();
        StoredFileInfo stored = storage.store(file, subFolder);

        String originalName = (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
            ? file.getOriginalFilename() : (reportType.name() + ".docx");
        String mimeType = (file.getContentType() != null && !file.getContentType().isBlank())
            ? file.getContentType() : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        String fileKey = extractStorageKey(stored.getProviderFileId() != null ? stored.getProviderFileId() : stored.getStoragePathOrUrl());

        ReportTemplateVersion entity = ReportTemplateVersion.builder()
            .term(term)
            .department(department)
            .program(program)
            .programCode(resolvedProgramCode)
            .title(title != null && !title.isBlank() ? title : originalName)
            .description(description)
            .reportType(reportType)
            .fileUrl(fileKey)
            .fileName(originalName)
            .fileSizeBytes(file.getSize())
            .mimeType(mimeType)
            .versionNumber(nextVersion)
            .isMandatory(isMandatory != null ? isMandatory : true)
            .isActive(true)
            .createdBy(uploader)
            .build();

        return ReportTemplateResponse.fromEntity(templateRepository.save(entity));
    }

    @Transactional
    public List<ReportTemplateResponse> inheritTemplates(
        UUID sourceTermId, UUID targetTermId, boolean overwrite, User actor
    ) {
        verifyFacultyAdminOrAdmin(actor);
        if (sourceTermId == null || targetTermId == null) {
            throw new BadRequestException("Học kỳ nguồn và học kỳ đích không được để trống");
        }
        if (sourceTermId.equals(targetTermId)) {
            throw new BadRequestException("Học kỳ nguồn và học kỳ đích phải khác nhau");
        }

        InternshipTerm targetTerm = termRepository.findById(targetTermId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", targetTermId));

        List<ReportTemplateVersion> sourceTemplates = templateRepository
            .findByTermIdAndIsActiveTrue(sourceTermId);
        if (sourceTemplates.isEmpty()) {
            throw new BadRequestException("Học kỳ nguồn không có biểu mẫu nào đang áp dụng để kế thừa");
        }

        List<ReportTemplateVersion> existingInTarget = templateRepository
            .findByTermIdAndIsActiveTrue(targetTermId);
        List<ReportTemplateVersion> inheritedList = new ArrayList<>();

        for (ReportTemplateVersion src : sourceTemplates) {
            boolean alreadyExists = existingInTarget.stream().anyMatch(e ->
                e.getReportType() == src.getReportType()
                && Objects.equals(e.getProgramCode(), src.getProgramCode())
            );
            if (alreadyExists && !overwrite) {
                continue;
            }

            int nextVersion = templateRepository
                .findFirstByTermIdAndReportTypeAndProgramCodeOrderByVersionNumberDesc(
                    targetTermId, src.getReportType(), src.getProgramCode())
                .map(t -> t.getVersionNumber() + 1).orElse(1);

            ReportTemplateVersion inherited = ReportTemplateVersion.builder()
                .term(targetTerm)
                .department(src.getDepartment())
                .program(src.getProgram())
                .programCode(src.getProgramCode() != null ? src.getProgramCode() : "DEFAULT")
                .title(src.getTitle())
                .description(src.getDescription())
                .reportType(src.getReportType())
                .fileUrl(src.getFileUrl())
                .fileName(src.getFileName())
                .fileSizeBytes(src.getFileSizeBytes())
                .mimeType(src.getMimeType())
                .versionNumber(nextVersion)
                .isMandatory(src.getIsMandatory() != null ? src.getIsMandatory() : true)
                .isActive(true)
                .createdBy(actor)
                .build();

            inheritedList.add(templateRepository.save(inherited));
        }

        return inheritedList.stream().map(ReportTemplateResponse::fromEntity).toList();
    }

    @Transactional
    public ReportTemplateResponse updateTemplate(UUID templateId, com.internlink.core.presentation.organization.dto.request.UpdateReportTemplateRequest request, User actor) {
        verifyFacultyAdminOrAdmin(actor);
        ReportTemplateVersion entity = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportTemplateVersion", "id", templateId));
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            entity.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription().trim());
        }
        if (request.getFileName() != null && !request.getFileName().isBlank()) {
            entity.setFileName(request.getFileName().trim());
        }
        if (request.getProgramCode() != null && !request.getProgramCode().isBlank()) {
            entity.setProgramCode(request.getProgramCode().trim());
        }
        if (request.getIsMandatory() != null) {
            entity.setIsMandatory(request.getIsMandatory());
        }
        return ReportTemplateResponse.fromEntity(templateRepository.save(entity));
    }

    @Transactional
    public ReportTemplateResponse toggleActive(UUID templateId, User actor) {
        verifyFacultyAdminOrAdmin(actor);
        ReportTemplateVersion entity = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportTemplateVersion", "id", templateId));
        entity.setIsActive(!Boolean.TRUE.equals(entity.getIsActive()));
        return ReportTemplateResponse.fromEntity(templateRepository.save(entity));
    }

    @Transactional
    public void deleteTemplate(UUID templateId, User actor) {
        verifyFacultyAdminOrAdmin(actor);
        ReportTemplateVersion entity = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportTemplateVersion", "id", templateId));
        templateRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public TemplateDownload loadTemplateDownload(UUID templateId) {
        ReportTemplateVersion entity = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportTemplateVersion", "id", templateId));
        String rawKey = entity.getFileUrl();
        String key = extractStorageKey(rawKey);

        Resource resource = null;
        if (!key.isBlank() && !key.startsWith("/api/v1/storage/")) {
            try {
                StorageService storage = storageServiceRouter.getActiveStorageService();
                resource = storage.loadAsResource(key);
            } catch (Exception ex) {
                log.warn("Storage template fetch failed for id={}, key={}. Falling back to sample generator: {}",
                    templateId, key, ex.getMessage());
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

    @Transactional(readOnly = true)
    public Resource loadTemplateResource(UUID templateId) {
        return loadTemplateDownload(templateId).resource();
    }

    public TemplateDownload generateFallbackDownload(ReportTemplateType reportType, String programCode) {
        Resource resource = sampleGenerator.generateSample(null, reportType, programCode);
        String fileName = sampleGenerator.resolveDefaultFileName(reportType, programCode);
        return new TemplateDownload(resource, fileName, "application/msword");
    }

    public TemplateDownload loadOfficialTemplateDocx(String templateCode, String programCode) {
        ReportTemplateType type = resolveReportTemplateType(templateCode);
        List<ReportTemplateVersion> uploaded = templateRepository.findByReportTypeAndIsActiveTrue(type);
        if (!uploaded.isEmpty()) {
            ReportTemplateVersion matched = uploaded.stream()
                .filter(t -> programCode != null && programCode.equalsIgnoreCase(t.getProgramCode()))
                .findFirst()
                .orElse(uploaded.get(0));
            if (matched.getFileUrl() != null && matched.getFileUrl().toLowerCase().endsWith(".docx")) {
                return loadTemplateDownload(matched.getId());
            }
        }
        Resource resource = sampleGenerator.generateSampleDocx(null, type, programCode);
        String fileName = sampleGenerator.resolveDefaultDocxFileName(type, programCode);
        return new TemplateDownload(resource, fileName, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    public ReportTemplateType resolveReportTemplateType(String templateCode) {
        if (templateCode == null) return ReportTemplateType.OTHER;
        try {
            return ReportTemplateType.valueOf(templateCode.trim().toUpperCase());
        } catch (Exception ex) {
            return switch (templateCode.trim().toUpperCase()) {
                case "04", "FORM_04", "FORM_04_8W" -> ReportTemplateType.FORM_04_RECEPTION_8W;
                case "04_12W", "FORM_04_12W" -> ReportTemplateType.FORM_04_RECEPTION_12W;
                case "M-TT-01", "M01_12W" -> ReportTemplateType.M01;
                case "M-TT-02" -> ReportTemplateType.M02;
                case "M-TT-03" -> ReportTemplateType.M03;
                case "M-TT-04", "M-TT-04-KHMT" -> ReportTemplateType.M04_LECTURER_KHMT;
                case "M-TT-04-MMT" -> ReportTemplateType.M04_LECTURER_MMT;
                case "M-TT-05" -> ReportTemplateType.M05;
                default -> ReportTemplateType.OTHER;
            };
        }
    }

    public TemplateDownload loadOfficialTemplate(String templateCode, String programCode) {
        ReportTemplateType type;
        try {
            type = ReportTemplateType.valueOf(templateCode.trim().toUpperCase());
        } catch (Exception ex) {
            type = switch (templateCode.trim().toUpperCase()) {
                case "04", "FORM_04", "FORM_04_8W" -> ReportTemplateType.FORM_04_RECEPTION_8W;
                case "04_12W", "FORM_04_12W" -> ReportTemplateType.FORM_04_RECEPTION_12W;
                case "M-TT-01", "M01_12W" -> ReportTemplateType.M01;
                case "M-TT-02" -> ReportTemplateType.M02;
                case "M-TT-03" -> ReportTemplateType.M03;
                case "M-TT-04", "M-TT-04-KHMT" -> ReportTemplateType.M04_LECTURER_KHMT;
                case "M-TT-04-MMT" -> ReportTemplateType.M04_LECTURER_MMT;
                case "M-TT-05" -> ReportTemplateType.M05;
                default -> ReportTemplateType.OTHER;
            };
        }
        // Ưu tiên tải tệp thật do Khoa đã upload lên hệ thống lưu trữ (R2/Storage)
        List<ReportTemplateVersion> uploaded = templateRepository.findByReportTypeAndIsActiveTrue(type);
        if (!uploaded.isEmpty()) {
            ReportTemplateVersion matched = uploaded.stream()
                .filter(t -> programCode != null && programCode.equalsIgnoreCase(t.getProgramCode()))
                .findFirst()
                .orElse(uploaded.get(0));
            return loadTemplateDownload(matched.getId());
        }

        Resource resource = sampleGenerator.generateSample(null, type, programCode);
        String fileName = sampleGenerator.resolveDefaultFileName(type, programCode);
        return new TemplateDownload(resource, fileName, "application/msword");
    }

    private String extractStorageKey(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return "";
        if (fileUrl.startsWith("r2://")) {
            int slashAfterBucket = fileUrl.indexOf('/', 5);
            return slashAfterBucket != -1 ? fileUrl.substring(slashAfterBucket + 1) : fileUrl;
        }
        return fileUrl;
    }

    private void verifyFacultyAdminOrAdmin(User user) {
        if (user == null || (user.getRole() != UserRole.FACULTY_ADMIN && user.getRole() != UserRole.ADMIN)) {
            throw new ForbiddenException("Chỉ Quản lý Khoa hoặc Quản trị viên mới có quyền quản lý biểu mẫu");
        }
    }
}
