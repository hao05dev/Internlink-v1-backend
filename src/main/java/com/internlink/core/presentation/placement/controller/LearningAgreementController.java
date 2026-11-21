package com.internlink.core.presentation.placement.controller;

import com.internlink.core.application.placement.LearningAgreementService;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.placement.dto.request.LearningAgreementRequest;
import com.internlink.core.presentation.placement.dto.response.LearningAgreementResponse;
import com.internlink.core.shared.api.ApiResponse;
import com.internlink.core.shared.enums.AgreementStatus;
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
@RequestMapping("/api/v1/agreements")
@RequiredArgsConstructor
public class LearningAgreementController {

    private final LearningAgreementService agreementService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'COMPANY_REP', 'ADMIN', 'FACULTY_ADMIN', 'LECTURER')")
    public ResponseEntity<ApiResponse<LearningAgreementResponse>> getAgreementById(@PathVariable UUID id) {
        LearningAgreementResponse response = agreementService.getAgreementById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/offer/{offerId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'COMPANY_REP', 'ADMIN', 'FACULTY_ADMIN', 'LECTURER')")
    public ResponseEntity<ApiResponse<LearningAgreementResponse>> getAgreementByOfferId(@PathVariable UUID offerId) {
        LearningAgreementResponse response = agreementService.getAgreementByOfferId(offerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/department/{departmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<List<LearningAgreementResponse>>> getAgreementsByDepartment(
        @PathVariable UUID departmentId,
        @RequestParam(required = false) AgreementStatus status
    ) {
        List<LearningAgreementResponse> list = agreementService.getAgreementsByDepartment(departmentId, status);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/my-agreements")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<LearningAgreementResponse>>> getMyAgreements(
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        List<LearningAgreementResponse> list = agreementService.getAgreementsByStudent(userDetail.getId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/company/mine")
    @PreAuthorize("hasRole('COMPANY_REP')")
    public ResponseEntity<ApiResponse<List<LearningAgreementResponse>>> getMyCompanyAgreements() {
        return ResponseEntity.ok(ApiResponse.success(agreementService.getAgreementsForOwnCompany()));
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<LearningAgreementResponse>> createAgreement(
        @AuthenticationPrincipal CustomUserDetail userDetail,
        @Valid @RequestBody LearningAgreementRequest request
    ) {
        LearningAgreementResponse response = agreementService.createAgreement(userDetail.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Khởi tạo Thỏa thuận học tập 3 bên thành công", response));
    }

    @PatchMapping("/{id}/sign")
    @PreAuthorize("hasAnyRole('STUDENT', 'COMPANY_REP', 'FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<LearningAgreementResponse>> signAgreement(
        @PathVariable UUID id,
        @AuthenticationPrincipal CustomUserDetail userDetail,
        @RequestBody Map<String, Object> signatureData
    ) {
        LearningAgreementResponse response = agreementService.signAgreement(id, userDetail.getRole().name(), signatureData);
        return ResponseEntity.ok(ApiResponse.success("Ký xác nhận Thỏa thuận học tập thành công", response));
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasRole('FACULTY_ADMIN')")
    public ResponseEntity<ApiResponse<LearningAgreementResponse>> reviewAgreement(
        @PathVariable UUID id,
        @RequestParam AgreementStatus status
    ) {
        LearningAgreementResponse response = agreementService.reviewAgreementByFaculty(id, status);
        return ResponseEntity.ok(ApiResponse.success("Phê duyệt Thỏa thuận học tập thành công", response));
    }
}
