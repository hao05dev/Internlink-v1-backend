package com.internlink.core.presentation.company.controller;

import com.internlink.core.application.company.CompanyService;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.company.dto.request.CompanyRequest;
import com.internlink.core.presentation.company.dto.response.CompanyResponse;
import com.internlink.core.shared.api.ApiResponse;
import com.internlink.core.shared.enums.VerificationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_ADMIN', 'SCHOOL_OFFICE')")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> getAllCompanies(
        @RequestParam(required = false) VerificationStatus status
    ) {
        List<CompanyResponse> companies = (status != null)
            ? companyService.getCompaniesByStatus(status)
            : companyService.getAllCompanies();
        return ResponseEntity.ok(ApiResponse.success(companies));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompanyById(@PathVariable UUID id) {
        CompanyResponse company = companyService.getCompanyById(id);
        return ResponseEntity.ok(ApiResponse.success(company));
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_REP')")
    public ResponseEntity<ApiResponse<CompanyResponse>> registerCompany(
        @Valid @RequestBody CompanyRequest request
    ) {
        CompanyResponse response = companyService.registerCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Đăng ký hồ sơ doanh nghiệp thành công", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY_REP')")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompany(
        @PathVariable UUID id,
        @Valid @RequestBody CompanyRequest request
    ) {
        CompanyResponse response = companyService.updateCompany(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin doanh nghiệp thành công", response));
    }

    @PatchMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<CompanyResponse>> verifyCompany(
        @PathVariable UUID id,
        @RequestParam VerificationStatus status,
        @RequestBody(required = false) Map<String, Object> verificationDetail,
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        CompanyResponse response = companyService.verifyCompany(id, status, verificationDetail, userDetail.getId());
        return ResponseEntity.ok(ApiResponse.success("Thẩm định doanh nghiệp thành công", response));
    }
}
