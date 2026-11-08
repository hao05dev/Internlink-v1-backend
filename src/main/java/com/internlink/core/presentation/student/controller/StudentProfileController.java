package com.internlink.core.presentation.student.controller;

import com.internlink.core.application.student.JobSearchContextService;
import com.internlink.core.application.student.StudentProfileService;
import com.internlink.core.presentation.student.dto.response.JobSearchContextResponse;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.student.dto.request.StudentProfileRequest;
import com.internlink.core.presentation.student.dto.response.StudentProfileResponse;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentProfileController {

    private final StudentProfileService studentProfileService;
    private final JobSearchContextService jobSearchContextService;

    @GetMapping("/me/profile")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> getMyProfile(
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        StudentProfileResponse profile = studentProfileService.getProfileByUserId(userDetail.getId());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @GetMapping("/me/job-search-context")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<JobSearchContextResponse>> getMyJobSearchContext(
        @AuthenticationPrincipal CustomUserDetail userDetail
    ) {
        return ResponseEntity.ok(ApiResponse.success(jobSearchContextService.forStudent(userDetail.getId())));
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> updateMyProfile(
        @AuthenticationPrincipal CustomUserDetail userDetail,
        @Valid @RequestBody StudentProfileRequest request
    ) {
        StudentProfileResponse profile = studentProfileService.createOrUpdateProfile(userDetail.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ sinh viên thành công", profile));
    }

    @GetMapping("/{userId}/profile")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_ADMIN', 'LECTURER', 'COMPANY_REP', 'COMPANY_MENTOR')")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> getProfileByUserId(@PathVariable UUID userId) {
        StudentProfileResponse profile = studentProfileService.getProfileByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }
}
