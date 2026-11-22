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
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaReportTemplateVersionRepository;
import com.internlink.core.presentation.organization.dto.request.UpdateReportTemplateRequest;
import com.internlink.core.presentation.organization.dto.response.ReportTemplateResponse;
import com.internlink.core.shared.enums.ReportTemplateType;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Quản lý biểu mẫu của Khoa: danh sách, upload, kế thừa giữa các kỳ, sửa, bật/tắt, xoá. */
@Service
@RequiredArgsConstructor
public class ReportTemplateService {

    private final JpaReportTemplateVersionRepository templateRepository;
    private final JpaInternshipTermRepository termRepository;
    private final JpaAcademicProgramRepository programRepository;
    private final StorageServiceRouter storageServiceRouter;
    private final TemplateAccessPolicy accessPolicy;

    /**
     * ADMIN và VP Trường xem được mọi khoa; các vai trò khác chỉ thấy mẫu của khoa mình
     * cộng mẫu chung cấp trường.
     */
    @Transactional(readOnly = true)
    public List<ReportTemplateResponse> getTemplates(UUID termId, UUID departmentId, Boolean activeOnly, User viewer) {
        boolean onlyActive = activeOnly == null || activeOnly;
        List<ReportTemplateVersion> list;
        if (viewer != null && (viewer.getRole() == UserRole.ADMIN || viewer.getRole() == UserRole.SCHOOL_OFFICE)) {
            list = (departmentId != null)
                ? (onlyActive
                    ? templateRepository.findByTermIdAndDepartmentIdAndIsActiveTrue(termId, departmentId)
                    : templateRepository.findByTermIdAndDepartmentIdOrderByCreatedAtDesc(termId, departmentId))
                : (onlyActive
                    ? templateRepository.findByTermIdAndIsActiveTrue(termId)
                    : templateRepository.findByTermIdOrderByCreatedAtDesc(termId));
        } else {
            list = templateRepository.findVisibleToDepartment(termId, TemplateAccessPolicy.departmentIdOf(viewer), onlyActive);
        }
        return list.stream().map(ReportTemplateResponse::fromEntity).toList();
    }

    @Transactional
    public ReportTemplateResponse uploadTemplate(
        UUID termId, UUID departmentId, UUID programId, String programCode,
        ReportTemplateType reportType, String title, String description,
        Boolean isMandatory, MultipartFile file, User uploader
    ) {
                if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tin biểu mẫu không được để trống");
        }

        InternshipTerm term = termRepository.findById(termId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        Department department = accessPolicy.resolveUploadDepartment(departmentId, uploader);

        AcademicProgram program = null;
        String resolvedProgramCode = programCode != null && !programCode.isBlank()
            ? programCode.trim().toUpperCase() : "DEFAULT";
        if (programId != null) {
            program = programRepository.findById(programId).orElse(null);
            if (program != null && (programCode == null || programCode.isBlank())) {
                resolvedProgramCode = program.getCode();
            }
        }

        int nextVersion = nextVersion(termId, department != null ? department.getId() : null, reportType, resolvedProgramCode);

        StorageService storage = storageServiceRouter.getActiveStorageService();
        String deptFolder = department != null && department.getCode() != null ? department.getCode().toLowerCase() : "school";
        String subFolder = "templates/" + termId + "/" + deptFolder + "/" + reportType.name().toLowerCase() + "/" + resolvedProgramCode.toLowerCase();
        StoredFileInfo stored = storage.store(file, subFolder);

        String originalName = (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
            ? file.getOriginalFilename() : (reportType.name() + ".docx");
        String mimeType = (file.getContentType() != null && !file.getContentType().isBlank())
            ? file.getContentType() : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        String fileKey = ReportTemplateDownloadService.extractStorageKey(stored.getProviderFileId() != null ? stored.getProviderFileId() : stored.getStoragePathOrUrl());

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
        accessPolicy.verifyManager(actor);
        if (sourceTermId == null || targetTermId == null) {
            throw new BadRequestException("Học kỳ nguồn và học kỳ đích không được để trống");
        }
        if (sourceTermId.equals(targetTermId)) {
            throw new BadRequestException("Học kỳ nguồn và học kỳ đích phải khác nhau");
        }

        InternshipTerm targetTerm = termRepository.findById(targetTermId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", targetTermId));

        List<ReportTemplateVersion> sourceTemplates = templateRepository
            .findByTermIdAndIsActiveTrue(sourceTermId).stream()
            .filter(t -> accessPolicy.canManage(actor, t))
            .toList();
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
                && TemplateAccessPolicy.sameDepartment(e, src)
            );
            if (alreadyExists && !overwrite) {
                continue;
            }

            int nextVersion = nextVersion(targetTermId, TemplateAccessPolicy.departmentIdOf(src), src.getReportType(), src.getProgramCode());

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
    public ReportTemplateResponse updateTemplate(UUID templateId, UpdateReportTemplateRequest request, User actor) {
        ReportTemplateVersion entity = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportTemplateVersion", "id", templateId));
        accessPolicy.verifyCanManage(actor, entity);
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
        ReportTemplateVersion entity = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportTemplateVersion", "id", templateId));
        accessPolicy.verifyCanManage(actor, entity);
        entity.setIsActive(!Boolean.TRUE.equals(entity.getIsActive()));
        return ReportTemplateResponse.fromEntity(templateRepository.save(entity));
    }

    @Transactional
    public void deleteTemplate(UUID templateId, User actor) {
        ReportTemplateVersion entity = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportTemplateVersion", "id", templateId));
        accessPolicy.verifyCanManage(actor, entity);
        templateRepository.delete(entity);
    }

    /** Số phiên bản đánh riêng theo (kỳ, khoa, loại mẫu, ngành). */
    private int nextVersion(UUID termId, UUID departmentId, ReportTemplateType type, String programCode) {
        return templateRepository
            .findFirstByTermIdAndDepartmentIdAndReportTypeAndProgramCodeOrderByVersionNumberDesc(
                termId, departmentId, type, programCode)
            .map(t -> t.getVersionNumber() + 1)
            .orElse(1);
    }
}
