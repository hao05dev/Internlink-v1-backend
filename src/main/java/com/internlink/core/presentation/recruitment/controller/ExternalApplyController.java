package com.internlink.core.presentation.recruitment.controller;

import com.internlink.core.application.recruitment.impl.ExternalApplyService;
import com.internlink.core.presentation.recruitment.dto.response.ExternalApplyJobOption;
import com.internlink.core.presentation.recruitment.dto.response.ExternalApplyStatsResponse;
import com.internlink.core.shared.api.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Kênh ứng tuyển ngoài hệ thống của tin EXTERNAL: ghi nhận lượt bấm và thống kê quan tâm. */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class ExternalApplyController {
    private final ExternalApplyService externalApply;

    @PostMapping("/{jobId}/external-apply/clicks")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Void>> recordClick(@PathVariable UUID jobId) {
        externalApply.recordClick(jobId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{jobId}/external-apply/stats")
    @PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN', 'FACULTY_ADMIN', 'COMPANY_REP')")
    public ResponseEntity<ApiResponse<ExternalApplyStatsResponse>> stats(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.success(externalApply.stats(jobId)));
    }

    /** Tin ứng tuyển ngoài SV đã mở kênh của DN – chọn làm tin nguồn của Phiếu tiếp nhận. */
    @GetMapping("/external-apply/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<ExternalApplyJobOption>>> myJobs() {
        return ResponseEntity.ok(ApiResponse.success(externalApply.myJobs()));
    }
}
