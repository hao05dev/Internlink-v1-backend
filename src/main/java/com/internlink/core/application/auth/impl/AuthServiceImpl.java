package com.internlink.core.application.auth.impl;

import com.internlink.core.application.auth.AuthService;
import com.internlink.core.application.auth.CompanyRegistrationService;
import com.internlink.core.application.auth.RefreshTokenService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CookieUtils;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.infrastructure.security.JwtUtil;
import com.internlink.core.infrastructure.security.TokenRevocationService;
import com.internlink.core.presentation.auth.dto.request.LoginRequest;
import com.internlink.core.presentation.auth.dto.request.RegisterCompanyRequest;
import com.internlink.core.presentation.auth.dto.response.AuthResponse;
import com.internlink.core.presentation.auth.dto.response.UserSummaryDto;
import com.internlink.core.shared.error.AppException;
import com.internlink.core.shared.error.ErrorCode;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final PasswordLoginService passwordLogin;
    private final JwtUtil jwtUtil;
    private final CookieUtils cookieUtils;
    private final JpaUserRepository users;
    private final TokenRevocationService revocations;
    private final RefreshTokenService refreshTokens;
    private final CompanyRegistrationService registration;
    private final AuthAuditService audit;

    @Override
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        return login(request, null, response);
    }

    @Override
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        User user = passwordLogin.authenticate(request.getEmail().trim().toLowerCase(Locale.ROOT), request.getPassword());
        if (user == null) {
            audit.record(null, "USER_LOGIN_FAILED", "FAILURE", httpRequest);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        refreshTokens.createSessionAndToken(user, httpRequest, response);
        String token = jwtUtil.generateToken(CustomUserDetail.create(user));
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtils.createAccessTokenCookie(token, jwtUtil.getExpirationMs()).toString());
        audit.record(user.getId(), "USER_LOGIN", "SUCCESS", httpRequest);
        return AuthResponseMapper.auth(user);
    }

    @Override
    public AuthResponse registerCompany(RegisterCompanyRequest request, HttpServletResponse response) {
        return registration.registerCompany(request, response);
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = cookieUtils.extractTokenFromCookie(request).orElseGet(() -> {
            String bearer = request == null ? null : request.getHeader(HttpHeaders.AUTHORIZATION);
            return bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : null;
        });
        if (token != null && jwtUtil.validateToken(token)) {
            revocations.revokeToken(jwtUtil.getJtiFromToken(token), jwtUtil.getExpirationDateFromToken(token));
        }
        refreshTokens.revokeSession(request, response);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetail principal) {
            audit.record(principal.getId(), "USER_LOGOUT", "SUCCESS", request);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieUtils.deleteAccessTokenCookie().toString());
        SecurityContextHolder.clearContext();
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryDto getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetail principal)) {
            throw new UnauthorizedException("Người dùng chưa đăng nhập");
        }
        User user = users.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
        return AuthResponseMapper.summary(user);
    }
}
