package com.internlink.core.presentation.organization.controller;

import com.internlink.core.application.organization.InternshipTermService;
import com.internlink.core.application.organization.IntroductionLetterNoticeService;
import com.internlink.core.shared.api.ApiResponse;
import com.internlink.core.shared.enums.TermStatus;
import com.internlink.core.presentation.organization.dto.request.InternshipTermRequest;
import com.internlink.core.presentation.organization.dto.response.InternshipTermResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class InternshipTermController {

    private final InternshipTermService termService;
    private final IntroductionLetterNoticeService introductionLetterNoticeService;

    /** {@code rosterIds} is optional; null notifies every student in the term. */
    public record IntroductionLetterNoticeRequest(
            String pickupLocation,
            LocalDate pickupDate,
            List<UUID> rosterIds) {}

    @PostMapping("/{id}/introduction-letter-notice")
    @PreAuthorize("hasRole('FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<IntroductionLetterNoticeService.NoticeResult>> notifyIntroductionLetterPickup(
        @PathVariable UUID id, @RequestBody IntroductionLetterNoticeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(introductionLetterNoticeService.send(
            id, request.pickupLocation(), request.pickupDate(), request.rosterIds())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InternshipTermResponse>>> getAllTerms() {
        return ResponseEntity.ok(ApiResponse.success(termService.getAllTerms()));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<InternshipTermResponse>>> getActiveTerms() {
        return ResponseEntity.ok(ApiResponse.success(termService.getActiveTerms()));
    }

    @GetMapping("/by-department/{departmentId}")
    public ResponseEntity<ApiResponse<List<InternshipTermResponse>>> getTermsByDepartment(
            @PathVariable UUID departmentId
    ) {
        List<InternshipTermResponse> terms = termService.getTermsByDepartment(departmentId);
        return ResponseEntity.ok(ApiResponse.success(terms));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InternshipTermResponse>> getTermById(@PathVariable UUID id) {
        InternshipTermResponse term = termService.getTermById(id);
        return ResponseEntity.ok(ApiResponse.success(term));
    }

    @PostMapping
    @PreAuthorize("hasRole('FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<InternshipTermResponse>> createTerm(
            @Valid @RequestBody InternshipTermRequest request
    ) {
        InternshipTermResponse response = termService.createTerm(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Khởi tạo kỳ thực tập thành công", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<InternshipTermResponse>> updateTermStatus(
            @PathVariable UUID id,
            @RequestParam TermStatus status
    ) {
        InternshipTermResponse response = termService.updateTermStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái kỳ thực tập thành công", response));
    }
}
