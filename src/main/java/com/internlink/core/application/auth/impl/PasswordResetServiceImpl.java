package com.internlink.core.application.auth.impl;

import com.internlink.core.application.auth.PasswordResetService;
import com.internlink.core.application.system.notification.EmailOutboxService;
import com.internlink.core.application.system.notification.EmailRenderer;
import com.internlink.core.application.system.notification.EmailTemplate;
import com.internlink.core.domain.auth.PasswordResetToken;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaAuthSessionFamilyRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaPasswordResetTokenRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.TokenCryptoUtils;
import com.internlink.core.presentation.auth.dto.request.ForgotPasswordRequest;
import com.internlink.core.presentation.auth.dto.request.ResetPasswordRequest;
import com.internlink.core.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Đảm nhiệm duy nhất 1 nhiệm vụ: Cấp phát token khôi phục và đặt lại mật khẩu an toàn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final JpaUserRepository userRepository;
    private final JpaPasswordResetTokenRepository tokenRepository;
    private final JpaAuthSessionFamilyRepository sessionFamilyRepository;
    private final TokenCryptoUtils cryptoUtils;
    private final PasswordEncoder passwordEncoder;
    private final EmailOutboxService emailOutboxService;
    private final EmailRenderer emailRenderer;
    private final com.internlink.core.application.auth.RecoveryRequestLimiter recoveryLimits;

    @Value("${internlink.email.public-base-url:http://localhost:3000}")
    private String publicBaseUrl;

    private static final long RESET_TOKEN_TTL_MINUTES = 60;

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        if (!recoveryLimits.allow(cleanEmail)) return;
        var userOpt = userRepository.findByEmail(cleanEmail);

        if (userOpt.isEmpty()) {
            // Không ném lỗi ra ngoài để chống tấn công dò tìm tài khoản (account enumeration)
            log.info("Yêu cầu quên mật khẩu cho email không tồn tại: [{}]", cleanEmail);
            return;
        }

        User user = userOpt.get();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Yêu cầu quên mật khẩu từ tài khoản bị khóa: [{}]", cleanEmail);
            return;
        }

        String rawToken = cryptoUtils.generateSecureRandomToken();
        String tokenHash = cryptoUtils.hashToken(rawToken);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .purpose("PASSWORD_RESET")
                .build();
        tokenRepository.save(resetToken);

        String resetUrl = publicBaseUrl + "/reset-password?token=" + rawToken;
        var content = emailRenderer.render(
                EmailTemplate.FORGOT_PASSWORD,
                java.util.Map.of(
                        "fullName", java.util.Objects.toString(user.getFullName(), ""),
                        "resetUrl", resetUrl,
                        "ttlMinutes", RESET_TOKEN_TTL_MINUTES));

        emailOutboxService.enqueueNotice(
                user.getEmail(),
                content,
                "PASSWORD_RESET",
                "pwd-reset:" + tokenHash
        );
        log.info("Đã tạo mã đặt lại mật khẩu cho user [{}]", user.getId());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        com.internlink.core.application.auth.PasswordPolicy.validate(request.getNewPassword());
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không khớp với mật khẩu mới");
        }

        String tokenHash = cryptoUtils.hashToken(request.getToken());
        PasswordResetToken resetToken = tokenRepository.findValidToken(tokenHash, OffsetDateTime.now())
                .orElseThrow(() -> new BadRequestException("Mã xác thực đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));

        User user = userRepository.findByIdForUpdate(resetToken.getUser().getId())
                .orElseThrow(() -> new BadRequestException("Tài khoản không hợp lệ"));
        if (!Boolean.TRUE.equals(user.getIsActive()) || !resetToken.getPurpose().equals(request.getPurpose())) {
            throw new BadRequestException("Liên kết không hợp lệ hoặc không còn hiệu lực");
        }
        OffsetDateTime now = OffsetDateTime.now();

        if (tokenRepository.consume(resetToken.getId(), now) != 1) {
            throw new BadRequestException("Liên kết đã được sử dụng hoặc hết hạn");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(now);
        user.setMustChangePassword(false);
        if ("ACCOUNT_ACTIVATION".equals(request.getPurpose())) user.setEmailVerifiedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        tokenRepository.invalidateAllForUser(user.getId(), now);

        sessionFamilyRepository.revokeAllActiveFamiliesByUserId(user.getId(), now, "PASSWORD_RESET");
        log.info("Người dùng [{}] đã đặt lại mật khẩu thành công qua email token", user.getId());
    }
}
