package com.internlink.core.presentation.organization.controller;

import com.internlink.core.application.organization.ReportTemplateService;
import com.internlink.core.application.organization.TemplateDownload;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.organization.dto.response.ReportTemplateResponse;
import com.internlink.core.shared.api.ApiResponse;
import com.internlink.core.shared.enums.ReportTemplateType;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization/templates")
@RequiredArgsConstructor
public class ReportTemplateController {

    private final ReportTemplateService templateService;
    private final JpaUserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReportTemplateResponse>>> getTemplates(
        @RequestParam UUID termId,
        @RequestParam(required = false) UUID departmentId,
        @RequestParam(required = false) Boolean activeOnly
    ) {
        return ResponseEntity.ok(ApiResponse.success(templateService.getTemplates(termId, departmentId, activeOnly)));
    }

    @GetMapping("/evaluation-form")
    public ResponseEntity<ApiResponse<ReportTemplateResponse>> getEvaluationForm(
        @RequestParam UUID termId,
        @RequestParam(required = false) String programCode
    ) {
        ReportTemplateResponse template = templateService.getEvaluationTemplateForStudent(termId, programCode);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReportTemplateResponse>> uploadTemplate(
        @RequestParam UUID termId,
        @RequestParam(required = false) UUID departmentId,
        @RequestParam(required = false) UUID programId,
        @RequestParam(required = false) String programCode,
        @RequestParam ReportTemplateType reportType,
        @RequestParam(required = false) String title,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) Boolean isMandatory,
        @RequestPart("file") MultipartFile file,
        @AuthenticationPrincipal CustomUserDetail principal
    ) {
        User uploader = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
        return ResponseEntity.ok(ApiResponse.success(
            templateService.uploadTemplate(
                termId, departmentId, programId, programCode, reportType, title, description, isMandatory, file, uploader)
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReportTemplateResponse>> updateTemplate(
        @PathVariable UUID id,
        @RequestBody com.internlink.core.presentation.organization.dto.request.UpdateReportTemplateRequest request,
        @AuthenticationPrincipal CustomUserDetail principal
    ) {
        User actor = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
        return ResponseEntity.ok(ApiResponse.success(templateService.updateTemplate(id, request, actor)));
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReportTemplateResponse>> toggleActive(
        @PathVariable UUID id,
        @AuthenticationPrincipal CustomUserDetail principal
    ) {
        User actor = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
        return ResponseEntity.ok(ApiResponse.success(templateService.toggleActive(id, actor)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(
        @PathVariable UUID id,
        @AuthenticationPrincipal CustomUserDetail principal
    ) {
        User actor = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
        templateService.deleteTemplate(id, actor);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/inherit")
    @PreAuthorize("hasAnyRole('FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ReportTemplateResponse>>> inheritTemplates(
        @RequestParam UUID sourceTermId,
        @RequestParam UUID targetTermId,
        @RequestParam(required = false, defaultValue = "false") boolean overwrite,
        @AuthenticationPrincipal CustomUserDetail principal
    ) {
        User actor = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
        List<ReportTemplateResponse> result = templateService.inheritTemplates(sourceTermId, targetTermId, overwrite, actor);
        return ResponseEntity.ok(ApiResponse.success("Kế thừa biểu mẫu thành công", result));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadTemplate(@PathVariable UUID id) {
        TemplateDownload download = templateService.loadTemplateDownload(id);
        return buildDownloadResponse(download.resource(), download.fileName(), download.mimeType());
    }

    @GetMapping("/official/{templateCode}/preview")
    public ResponseEntity<Resource> previewOfficialTemplate(
        @PathVariable String templateCode,
        @RequestParam(required = false, defaultValue = "DEFAULT") String programCode
    ) {
        TemplateDownload download = templateService.loadOfficialTemplateDocx(templateCode, programCode);
        return buildPreviewResponse(download.resource(), download.fileName(), download.mimeType());
    }

    @GetMapping("/official/{templateCode}/download")
    public ResponseEntity<Resource> downloadOfficialTemplate(
        @PathVariable String templateCode,
        @RequestParam(required = false, defaultValue = "DEFAULT") String programCode
    ) {
        TemplateDownload download = templateService.loadOfficialTemplate(templateCode, programCode);
        return buildDownloadResponse(download.resource(), download.fileName(), download.mimeType());
    }

    @GetMapping("/fallback/{reportType}")
    public ResponseEntity<Resource> downloadFallback(
        @PathVariable ReportTemplateType reportType,
        @RequestParam(required = false, defaultValue = "DEFAULT") String programCode
    ) {
        TemplateDownload download = templateService.generateFallbackDownload(reportType, programCode);
        return buildDownloadResponse(download.resource(), download.fileName(), download.mimeType());
    }

    private ResponseEntity<Resource> buildPreviewResponse(Resource resource, String filename, String mimeType) {
        String safeName = (filename != null && !filename.isBlank()) ? filename : "bieu-mau.docx";
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (mimeType != null && !mimeType.isBlank()) {
                mediaType = MediaType.parseMediaType(mimeType);
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
            .body(resource);
    }

    private ResponseEntity<Resource> buildDownloadResponse(Resource resource, String filename, String mimeType) {
        String safeName = (filename != null && !filename.isBlank()) ? filename : "bieu-mau.docx";
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (mimeType != null && !mimeType.isBlank()) {
                mediaType = MediaType.parseMediaType(mimeType);
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
            .body(resource);
    }
}
