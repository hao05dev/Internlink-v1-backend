package com.internlink.core.presentation.placement.controller;

import com.internlink.core.application.placement.StudentDossierService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.placement.dto.response.StudentDossierResponse;
import com.internlink.core.shared.api.ApiResponse;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students/my-dossier")
@RequiredArgsConstructor
public class StudentDossierController {

    private final StudentDossierService dossierService;
    private final JpaUserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentDossierResponse>> getMyDossier(
        @AuthenticationPrincipal CustomUserDetail principal
    ) {
        User student = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
        return ResponseEntity.ok(ApiResponse.success(dossierService.getStudentDossier(student)));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentDossierResponse>> uploadDossierDocument(
        @RequestParam(name = "placementId", required = false) UUID placementId,
        @RequestParam String templateCode,
        @RequestPart("file") MultipartFile file,
        @AuthenticationPrincipal CustomUserDetail principal
    ) {
        User student = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
        if (placementId == null) {
            placementId = dossierService.getStudentDossier(student).getPlacementId();
        }
        StudentDossierResponse response = dossierService.uploadDossierDocument(placementId, templateCode, file, student);
        return ResponseEntity.ok(ApiResponse.success("Nộp tệp hồ sơ thành công", response));
    }
}
