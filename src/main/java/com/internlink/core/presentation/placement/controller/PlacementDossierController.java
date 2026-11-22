package com.internlink.core.presentation.placement.controller;

import com.internlink.core.application.placement.dossier.PlacementDossierReviewService;
import com.internlink.core.presentation.placement.dto.request.DossierReviewRequest;
import com.internlink.core.presentation.placement.dto.response.StudentDossierResponse;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Hồ sơ nộp Khoa của một lần thực tập: GVHD/Khoa xem, GVHD duyệt từng file. */
@RestController
@RequestMapping("/api/v1/placements/{placementId}/dossier")
@RequiredArgsConstructor
public class PlacementDossierController {

    private final PlacementDossierReviewService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('LECTURER', 'FACULTY_ADMIN', 'ADMIN')")
    public ApiResponse<StudentDossierResponse> get(@PathVariable UUID placementId) {
        return ApiResponse.success(service.getDossier(placementId));
    }

    @PatchMapping("/{documentId}/review")
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<StudentDossierResponse> review(@PathVariable UUID placementId, @PathVariable UUID documentId,
                                                      @Valid @RequestBody DossierReviewRequest request) {
        return ApiResponse.success("Đã cập nhật kết quả duyệt hồ sơ",
            service.review(placementId, documentId, request.decision(), request.note()));
    }
}
