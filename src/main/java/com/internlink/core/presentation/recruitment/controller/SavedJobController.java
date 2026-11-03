package com.internlink.core.presentation.recruitment.controller;

import com.internlink.core.application.recruitment.SavedJobService;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.recruitment.dto.response.JobPositionResponse;
import com.internlink.core.shared.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/saved-jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class SavedJobController {

    private final SavedJobService savedJobService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobPositionResponse>>> getSavedJobs(
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        return ResponseEntity.ok(ApiResponse.success(savedJobService.getSavedJobs(userDetail.getId())));
    }

    @GetMapping("/ids")
    public ResponseEntity<ApiResponse<List<UUID>>> getSavedJobIds(
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        return ResponseEntity.ok(ApiResponse.success(savedJobService.getSavedJobIds(userDetail.getId())));
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<ApiResponse<Void>> saveJob(
        @AuthenticationPrincipal CustomUserDetail userDetail,
        @PathVariable UUID jobId
    ) {
        savedJobService.saveJob(userDetail.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success("Đã lưu tin tuyển dụng"));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<ApiResponse<Void>> unsaveJob(
        @AuthenticationPrincipal CustomUserDetail userDetail,
        @PathVariable UUID jobId
    ) {
        savedJobService.unsaveJob(userDetail.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success("Đã bỏ lưu tin tuyển dụng"));
    }
}
