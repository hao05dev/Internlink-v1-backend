package com.internlink.core.presentation.auth.controller;

import com.internlink.core.application.auth.GoogleAuthService;
import com.internlink.core.presentation.auth.dto.request.GoogleLoginRequest;
import com.internlink.core.presentation.auth.dto.response.AuthResponse;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đảm nhiệm duy nhất 1 nhiệm vụ: Phân phối yêu cầu xác thực đăng nhập qua mạng xã hội / OAuth.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class OAuthController {

    private final GoogleAuthService googleAuthService;
    private final com.internlink.core.application.auth.GoogleAccountLinkService googleLinks;

    @PostMapping("/google/link")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> link(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.internlink.core.infrastructure.security.CustomUserDetail principal,
            @Valid @RequestBody GoogleLoginRequest request) {
        googleLinks.link(principal.getId(), request.getIdToken());
        return ResponseEntity.ok(ApiResponse.success("Đã liên kết Google", null));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/google/link")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> unlink(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.internlink.core.infrastructure.security.CustomUserDetail principal) {
        googleLinks.unlink(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã huỷ liên kết Google", null));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AuthResponse authResponse = googleAuthService.authenticateWithGoogle(request, httpRequest, response);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập bằng Google thành công", authResponse));
    }
}
