package com.internlink.core.presentation.placement.controller;

import com.internlink.core.application.placement.AcceptanceFormOfficeService;
import com.internlink.core.presentation.placement.dto.response.StudentFoundResponse;
import com.internlink.core.shared.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Văn phòng Trường CNTT-TT: tiếp nhận bản giấy Phiếu tiếp nhận và phát giấy tờ thực tập. */
@RestController @RequestMapping("/api/v1/student-found/office") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
public class AcceptanceFormOfficeController {
    private final AcceptanceFormOfficeService service;

    @GetMapping
    public ApiResponse<List<StudentFoundResponse>> intake(@RequestParam(required = false) UUID termId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID departmentId) {
        return ApiResponse.success(service.intake(termId, status, departmentId));
    }
    @PatchMapping("/{id}/receive")
    public ApiResponse<StudentFoundResponse> receive(@PathVariable UUID id) {
        return ApiResponse.success(service.confirmReceived(id));
    }
    @PatchMapping("/{id}/return")
    public ApiResponse<StudentFoundResponse> returnToStudent(@PathVariable UUID id, @RequestParam String note) {
        return ApiResponse.success(service.returnToStudent(id, note));
    }
    @PatchMapping("/{id}/documents-collected")
    public ApiResponse<StudentFoundResponse> documentsCollected(@PathVariable UUID id) {
        return ApiResponse.success(service.confirmDocumentsCollected(id));
    }
}
