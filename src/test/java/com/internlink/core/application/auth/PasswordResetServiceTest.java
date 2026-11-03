package com.internlink.core.application.auth;

import com.internlink.core.application.auth.impl.PasswordResetServiceImpl;
import com.internlink.core.application.system.notification.EmailOutboxService;
import com.internlink.core.application.system.notification.EmailRenderer;
import com.internlink.core.application.system.notification.RenderedEmail;
import com.internlink.core.infrastructure.integration.email.EmailProperties;
import org.mockito.ArgumentCaptor;
import com.internlink.core.domain.auth.PasswordResetToken;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaAuthSessionFamilyRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaPasswordResetTokenRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.TokenCryptoUtils;
import com.internlink.core.presentation.auth.dto.request.ForgotPasswordRequest;
import com.internlink.core.presentation.auth.dto.request.ResetPasswordRequest;
import com.internlink.core.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private JpaUserRepository userRepository;

    @Mock
    private JpaPasswordResetTokenRepository tokenRepository;

    @Mock
    private JpaAuthSessionFamilyRepository sessionFamilyRepository;

    @Mock
    private TokenCryptoUtils cryptoUtils;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailOutboxService emailOutboxService;

    private PasswordResetServiceImpl service;
    private final RecoveryRequestLimiter recoveryLimits = mock(RecoveryRequestLimiter.class);

    @BeforeEach
    void setUp() {
        service = new PasswordResetServiceImpl(
                userRepository,
                tokenRepository,
                sessionFamilyRepository,
                cryptoUtils,
                passwordEncoder,
                emailOutboxService,
                new EmailRenderer(new EmailProperties()),
                recoveryLimits
        );
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://localhost:3000");
        lenient().when(recoveryLimits.allow(any())).thenReturn(true);
    }

    @Test
    void shouldCreateResetTokenAndEnqueueEmailOnForgotPassword() {
        User user = User.builder()
                .email("student@internlink.vn")
                .fullName("Nguyen Van A")
                .isActive(true)
                .build();
        user.setId(UUID.randomUUID());

        when(userRepository.findByEmail("student@internlink.vn")).thenReturn(Optional.of(user));
        when(cryptoUtils.generateSecureRandomToken()).thenReturn("raw-token-123");
        when(cryptoUtils.hashToken("raw-token-123")).thenReturn("hashed-token-123");

        service.forgotPassword(ForgotPasswordRequest.builder().email("student@internlink.vn").build());

        verify(tokenRepository).save(any(PasswordResetToken.class));
        ArgumentCaptor<RenderedEmail> content = ArgumentCaptor.forClass(RenderedEmail.class);
        verify(emailOutboxService).enqueueNotice(
                eq("student@internlink.vn"),
                content.capture(),
                eq("PASSWORD_RESET"),
                eq("pwd-reset:hashed-token-123")
        );
        assertThat(content.getValue().subject()).isEqualTo("[InternLink] Yêu cầu đặt lại mật khẩu");
        assertThat(content.getValue().text())
                .contains("http://localhost:3000/reset-password?token=raw-token-123");
        assertThat(content.getValue().html()).contains("Nguyen Van A");
    }

    @Test
    void shouldSilentIgnoreWhenEmailDoesNotExistOnForgotPassword() {
        when(userRepository.findByEmail("unknown@internlink.vn")).thenReturn(Optional.empty());

        service.forgotPassword(ForgotPasswordRequest.builder().email("unknown@internlink.vn").build());

        verify(tokenRepository, never()).save(any());
        verify(emailOutboxService, never()).enqueueNotice(any(), any(RenderedEmail.class), any(), any());
    }

    @Test
    void shouldResetPasswordSuccessfullyWithValidToken() {
        User user = User.builder()
                .email("student@internlink.vn")
                .passwordHash("old-hash")
                .failedLoginAttempts(3)
                .lockedUntil(OffsetDateTime.now().plusMinutes(10))
                .build();
        user.setId(UUID.randomUUID());

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash("hash-abc")
                .expiresAt(OffsetDateTime.now().plusMinutes(30))
                .build();

        when(cryptoUtils.hashToken("raw-token")).thenReturn("hash-abc");
        when(tokenRepository.findValidToken(eq("hash-abc"), any())).thenReturn(Optional.of(resetToken));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.consume(any(), any())).thenReturn(1);
        when(passwordEncoder.encode("My new secret passphrase")).thenReturn("new-encoded-hash");

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("raw-token")
                .newPassword("My new secret passphrase")
                .confirmPassword("My new secret passphrase")
                .build();

        service.resetPassword(request);

        assertThat(user.getPasswordHash()).isEqualTo("new-encoded-hash");
        assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(user.getLockedUntil()).isNull();
        verify(tokenRepository).consume(any(), any());

        verify(userRepository).save(user);
        verify(tokenRepository).invalidateAllForUser(eq(user.getId()), any());
        verify(sessionFamilyRepository).revokeAllActiveFamiliesByUserId(eq(user.getId()), any(), eq("PASSWORD_RESET"));
    }

    @Test
    void shouldRejectResetPasswordWhenTokenIsInvalidOrExpired() {
        when(cryptoUtils.hashToken("expired-token")).thenReturn("expired-hash");
        when(tokenRepository.findValidToken(eq("expired-hash"), any())).thenReturn(Optional.empty());

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("expired-token")
                .newPassword("My new secret passphrase")
                .confirmPassword("My new secret passphrase")
                .build();

        assertThatThrownBy(() -> service.resetPassword(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không hợp lệ hoặc đã hết hạn");
    }
}
