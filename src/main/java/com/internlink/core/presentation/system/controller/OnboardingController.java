package com.internlink.core.presentation.system.controller;

import com.internlink.core.application.system.OnboardingProgressService;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Tiến độ tour hướng dẫn (mascot CapyLink) của người đang đăng nhập. */
@RestController
@RequestMapping("/api/v1/me/onboarding")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class OnboardingController {
    private final OnboardingProgressService service;

    @GetMapping
    public ApiResponse<List<OnboardingProgressService.Progress>> list() {
        return ApiResponse.success(service.list());
    }

    @PutMapping("/{tourKey}")
    public ApiResponse<OnboardingProgressService.Progress> save(
            @PathVariable String tourKey,
            @Valid @RequestBody OnboardingProgressService.UpdateRequest request) {
        return ApiResponse.success(service.save(tourKey, request));
    }
}
