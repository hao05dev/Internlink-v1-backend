package com.internlink.core.presentation.placement.controller;

import com.internlink.core.application.placement.AcceptanceFormReviewService;
import com.internlink.core.application.placement.StudentFoundApplicationService;
import com.internlink.core.presentation.placement.dto.request.StudentFoundRequest;
import com.internlink.core.presentation.placement.dto.response.StudentFoundResponse;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Phiếu tiếp nhận: SV điền/nộp phiếu; Khoa duyệt, phân công GVHD và công bố danh sách. */
@RestController @RequestMapping("/api/v1/student-found") @RequiredArgsConstructor
public class StudentFoundApplicationController {
    private final StudentFoundApplicationService service;
    private final AcceptanceFormReviewService reviewService;

    @PostMapping @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentFoundResponse> create(@Valid @RequestBody StudentFoundRequest request) {
        return ApiResponse.success(service.create(request));
    }
    @PutMapping("/{id}") @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentFoundResponse> update(@PathVariable UUID id, @Valid @RequestBody StudentFoundRequest request) {
        return ApiResponse.success(service.update(id, request));
    }
    @PatchMapping("/{id}/submit") @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentFoundResponse> submit(@PathVariable UUID id, @RequestParam UUID documentId) {
        return ApiResponse.success(service.submit(id, documentId));
    }
    @GetMapping("/mine") @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<StudentFoundResponse>> mine() { return ApiResponse.success(service.mine()); }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'LECTURER', 'FACULTY_ADMIN', 'SCHOOL_OFFICE', 'ADMIN')")
    public ApiResponse<StudentFoundResponse> byId(@PathVariable UUID id) { return ApiResponse.success(service.byId(id)); }

    @PatchMapping("/{id}/review") @PreAuthorize("hasAnyRole('FACULTY_ADMIN', 'ADMIN')")
    public ApiResponse<StudentFoundResponse> review(@PathVariable UUID id, @RequestParam String decision,
        @RequestParam(required = false) String note, @RequestParam(required = false) UUID lecturerId) {
        return ApiResponse.success(reviewService.review(id, decision, note, lecturerId));
    }
    @GetMapping("/term/{termId}") @PreAuthorize("hasAnyRole('FACULTY_ADMIN', 'SCHOOL_OFFICE', 'ADMIN')")
    public ApiResponse<List<StudentFoundResponse>> byTerm(@PathVariable UUID termId) {
        return ApiResponse.success(reviewService.byTerm(termId));
    }
    @PostMapping("/term/{termId}/publish") @PreAuthorize("hasAnyRole('FACULTY_ADMIN', 'ADMIN')")
    public ApiResponse<Map<String, Object>> publish(@PathVariable UUID termId) {
        int count = reviewService.publish(termId);
        return ApiResponse.success(Map.of("publishedCount", count,
            "message", "Đã công bố danh sách " + count + " sinh viên thực tập"));
    }
    @PostMapping("/term/{termId}/remind") @PreAuthorize("hasAnyRole('FACULTY_ADMIN', 'ADMIN')")
    public ApiResponse<Map<String, Object>> remindTerm(@PathVariable UUID termId) {
        int count = reviewService.remindStudentsWithoutForm(termId);
        return ApiResponse.success(Map.of("remindedCount", count,
            "message", "Đã gửi thông báo nhắc nhở cho " + count + " sinh viên"));
    }
}
