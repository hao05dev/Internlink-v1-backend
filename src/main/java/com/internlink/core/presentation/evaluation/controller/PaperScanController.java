package com.internlink.core.presentation.evaluation.controller;

import com.internlink.core.application.evaluation.scan.PaperScanService;
import com.internlink.core.presentation.evaluation.dto.request.PaperScanConfirmRequest;
import com.internlink.core.presentation.evaluation.dto.request.PaperScanRequest;
import com.internlink.core.presentation.evaluation.dto.response.PaperScanResponse;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Số hóa Phiếu đánh giá của cơ quan (M-TT-03) từ ảnh chụp; chỉ GVHD phụ trách lần thực tập. */
@RestController
@RequestMapping("/api/v1/evaluations/paper-scans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('LECTURER')")
public class PaperScanController {
    private final PaperScanService service;

    @PostMapping
    public ApiResponse<PaperScanResponse> scan(@Valid @RequestBody PaperScanRequest request) {
        return ApiResponse.success("Đã đọc phiếu, thầy/cô soát lại trước khi xác nhận",
                service.scan(request.placementId(), request.documentId()));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<PaperScanResponse> confirm(@PathVariable UUID id, @Valid @RequestBody PaperScanConfirmRequest request) {
        return ApiResponse.success("Đã ghi điểm cơ quan từ phiếu M-TT-03", service.confirm(id, request));
    }

    @GetMapping("/latest")
    public ApiResponse<PaperScanResponse> latest(@RequestParam UUID placementId) {
        return ApiResponse.success(service.latest(placementId).orElse(null));
    }
}
