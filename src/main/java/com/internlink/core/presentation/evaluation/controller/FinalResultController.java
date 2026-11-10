package com.internlink.core.presentation.evaluation.controller;

import com.internlink.core.application.evaluation.FinalResultService;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.evaluation.dto.request.BatchPublishResultRequest;
import com.internlink.core.presentation.evaluation.dto.request.FinalResultRequest;
import com.internlink.core.presentation.evaluation.dto.response.BatchPublishResultResponse;
import com.internlink.core.presentation.evaluation.dto.response.FacultyEvaluationSummaryResponse;
import com.internlink.core.presentation.evaluation.dto.response.FinalResultResponse;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/final-results")
@RequiredArgsConstructor
public class FinalResultController {

    private final FinalResultService finalResultService;

    /**
     * Xem kết quả đánh giá đợt thực tập.
     * Sinh viên chỉ được xem khi điểm đã được công bố chính thức.
     */
    @GetMapping("/placement/{placementId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'COMPANY_MENTOR', 'LECTURER', 'FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinalResultResponse>> getFinalResultByPlacement(
        @PathVariable UUID placementId,
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        FinalResultResponse response = finalResultService.getFinalResultByPlacement(placementId, userDetail.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Lấy bảng tổng hợp đánh giá và điểm số thực tập toàn bộ sinh viên trong kỳ (dành cho Quản lý Khoa).
     */
    @GetMapping("/term/{termId}/summary")
    @PreAuthorize("hasAnyRole('LECTURER', 'FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<FacultyEvaluationSummaryResponse>>> getTermEvaluationSummary(
        @PathVariable UUID termId,
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        List<FacultyEvaluationSummaryResponse> list = finalResultService.getTermEvaluationSummary(termId, userDetail.getId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * Tính toán và tổng hợp điểm tổng kết (hỗ trợ tổng hợp từ Rubrics, quy đổi thang điểm CTU).
     * Mặc định lưu DRAFT để Hội đồng rà soát (trừ khi request.publishImmediately = true).
     */
    @PostMapping("/finalize")
    @PreAuthorize("hasAnyRole('LECTURER', 'FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinalResultResponse>> finalizeResult(
        @AuthenticationPrincipal CustomUserDetail userDetail,
        @Valid @RequestBody FinalResultRequest request
    ) {
        FinalResultResponse response = finalResultService.calculateAndFinalizeResult(userDetail.getId(), request);
        String message = Boolean.TRUE.equals(request.getPublishImmediately())
            ? "Tổng hợp và công bố điểm tổng kết đợt thực tập thành công"
            : "Tổng hợp điểm tổng kết thành công (đang lưu bản nháp chờ xét duyệt)";
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(message, response));
    }

    /**
     * Tự động tổng hợp điểm nhanh cho 1 lần thực tập từ các phiếu đánh giá hiện có.
     */
    @PostMapping("/placement/{placementId}/quick-finalize")
    @PreAuthorize("hasAnyRole('LECTURER', 'FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinalResultResponse>> quickFinalize(
        @PathVariable UUID placementId,
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        FinalResultResponse response = finalResultService.quickFinalizePlacement(placementId, userDetail.getId());
        return ResponseEntity.ok(ApiResponse.success("Tổng hợp điểm thành công", response));
    }

    /**
     * Công bố chính thức kết quả thực tập cho 1 sinh viên.
     * Chuyển trạng thái lần thực tập sang COMPLETED nếu sinh viên đạt (PASSED).
     */
    @PatchMapping("/placement/{placementId}/publish")
    @PreAuthorize("hasAnyRole('LECTURER', 'FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinalResultResponse>> publishResult(
        @PathVariable UUID placementId,
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        FinalResultResponse response = finalResultService.publishFinalResult(placementId, userDetail.getId());
        return ResponseEntity.ok(ApiResponse.success("Công bố kết quả thực tập chính thức thành công", response));
    }

    /**
     * Công bố điểm hàng loạt cho các sinh viên trong kỳ thực tập.
     */
    @PostMapping("/term/{termId}/publish-batch")
    @PreAuthorize("hasAnyRole('LECTURER', 'FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<BatchPublishResultResponse>> publishBatchResults(
        @PathVariable UUID termId,
        @RequestBody(required = false) BatchPublishResultRequest request,
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        BatchPublishResultResponse response = finalResultService.publishBatchResults(termId, request, userDetail.getId());
        return ResponseEntity.ok(ApiResponse.success("Thực hiện công bố điểm hàng loạt thành công", response));
    }

    /**
     * Tổng hợp điểm hàng loạt cho toàn bộ sinh viên đã đủ điều kiện trong kỳ.
     */
    @PostMapping("/term/{termId}/finalize-batch")
    @PreAuthorize("hasAnyRole('LECTURER', 'FACULTY_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<com.internlink.core.presentation.evaluation.dto.response.BatchFinalizeResponse>> finalizeBatchResults(
        @PathVariable UUID termId,
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        var response = finalResultService.batchFinalizeTerm(termId, userDetail.getId());
        return ResponseEntity.ok(ApiResponse.success("Thực hiện tổng hợp điểm hàng loạt thành công", response));
    }
}
