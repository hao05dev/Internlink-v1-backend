package com.internlink.core.presentation.company.controller;

import com.internlink.core.application.company.CompanyProfileService;
import com.internlink.core.presentation.company.dto.request.CompanyProfileRequest;
import com.internlink.core.presentation.company.dto.response.CompanyPublicResponse;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/me/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMPANY_REP')")
public class CompanyProfileController {
    private final CompanyProfileService profiles;

    @GetMapping
    public ApiResponse<CompanyPublicResponse> get() {
        return ApiResponse.success(profiles.getOwnProfile());
    }

    @GetMapping("/mentors")
    public ApiResponse<java.util.List<com.internlink.core.presentation.company.dto.response.CompanyMentorResponse>> mentors() {
        return ApiResponse.success(profiles.getOwnMentors());
    }

    @PutMapping
    public ApiResponse<CompanyPublicResponse> update(@Valid @RequestBody CompanyProfileRequest request) {
        return ApiResponse.success(profiles.updateOwnProfile(request));
    }
}
