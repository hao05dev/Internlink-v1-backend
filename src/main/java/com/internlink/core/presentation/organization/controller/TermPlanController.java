package com.internlink.core.presentation.organization.controller;

import com.internlink.core.application.organization.TermPlanService;
import com.internlink.core.shared.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Kế hoạch TTTT của kỳ: VP Trường soạn và công bố; mọi người dùng xem bản đã công bố. */
@RestController
@RequestMapping("/api/v1/terms/{termId}/plan")
@RequiredArgsConstructor
public class TermPlanController {
    private final TermPlanService plans;

    @GetMapping
    public ResponseEntity<ApiResponse<TermPlanService.TermPlan>> get(@PathVariable UUID termId) {
        return ResponseEntity.ok(ApiResponse.success(plans.get(termId)));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<TermPlanService.TermPlan>> save(
            @PathVariable UUID termId, @RequestBody TermPlanService.SavePlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã lưu kế hoạch TTTT", plans.save(termId, request)));
    }

    @PostMapping("/publish")
    @PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<TermPlanService.PublishResult>> publish(@PathVariable UUID termId) {
        var result = plans.publish(termId);
        return ResponseEntity.ok(ApiResponse.success(
            "Đã công bố kế hoạch TTTT tới " + result.students() + " SV và " + result.staff() + " cán bộ", result));
    }
}
