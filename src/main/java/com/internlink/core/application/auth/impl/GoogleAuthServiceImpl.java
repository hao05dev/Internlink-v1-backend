package com.internlink.core.application.auth.impl;

import com.internlink.core.application.auth.GoogleAuthService;
import com.internlink.core.application.auth.RefreshTokenService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CookieUtils;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.infrastructure.security.GoogleTokenVerifier;
import com.internlink.core.infrastructure.security.JwtUtil;
import com.internlink.core.presentation.auth.dto.request.GoogleLoginRequest;
import com.internlink.core.presentation.auth.dto.response.AuthResponse;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.AccountLockedException;
import com.internlink.core.shared.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Đảm nhiệm duy nhất 1 nhiệm vụ: Phối hợp xác thực tài khoản Google OAuth,
 * liên kết tài khoản nội bộ và phát hành phiên làm việc an toàn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final JpaUserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;
    private final CookieUtils cookieUtils;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier tokenVerifier;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.internlink.core.application.system.AuditLogService auditLogService;

    @Override
    @Transactional
    public AuthResponse authenticateWithGoogle(
            GoogleLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        GoogleTokenVerifier.GoogleUserInfo googleUser = tokenVerifier.verify(request.getIdToken());

        if (!googleUser.emailVerified()) {
            throw new UnauthorizedException("Email tài khoản Google chưa được xác thực");
        }

        // Only a previously linked subject can authenticate; email alone cannot link roles.
        User user = userRepository.findByGoogleSubject(googleUser.sub())
                .orElseThrow(() -> new UnauthorizedException("Tài khoản Google này chưa được liên kết. Hãy đăng nhập bằng email và mật khẩu, rồi bấm \"Liên kết Google\" ở thanh trên cùng để lần sau đăng nhập bằng Google."));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Tài khoản của bạn đã bị khóa hoặc chưa kích hoạt");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            throw new AccountLockedException("Tài khoản đang bị tạm khóa. Vui lòng thử lại sau.");
        }

        user.setLastLoginAt(OffsetDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        CustomUserDetail principal = CustomUserDetail.create(user);
        String accessToken = jwtUtil.generateToken(principal);

        ResponseCookie accessCookie = cookieUtils.createAccessTokenCookie(accessToken, jwtUtil.getExpirationMs());
        com.internlink.core.infrastructure.security.AuthCookieWriter.add(response, HttpHeaders.SET_COOKIE, accessCookie.toString());

        refreshTokenService.createSessionAndToken(user, httpRequest, response);

        if (auditLogService != null) {
            try {
                String ip = extractClientIp(httpRequest);
                String ua = httpRequest != null ? httpRequest.getHeader("User-Agent") : null;
                java.util.Map<String, Object> authMeta = new java.util.HashMap<>();
                authMeta.put("email", user.getEmail());
                authMeta.put("role", user.getRole() != null ? user.getRole().name() : "STUDENT");
                authMeta.put("authMethod", "GOOGLE");

                auditLogService.logAction(
                    user.getId(),
                    "USER_LOGIN",
                    "USER",
                    user.getId(),
                    "SUCCESS",
                    authMeta,
                    ip,
                    ua,
                    authMeta
                );
            } catch (Exception ex) {
                log.debug("Lỗi ghi audit log Google login: {}", ex.getMessage());
            }
        }

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .mustChangePassword(user.getMustChangePassword())
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return null;
        return request.getRemoteAddr();
    }
}
