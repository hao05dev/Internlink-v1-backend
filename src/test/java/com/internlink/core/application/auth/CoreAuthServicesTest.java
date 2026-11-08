package com.internlink.core.application.auth;

import com.internlink.core.application.auth.impl.*;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.*;
import com.internlink.core.presentation.auth.dto.request.LoginRequest;
import com.internlink.core.shared.error.AppException;
import com.internlink.core.shared.error.ErrorCode;
import com.internlink.core.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreAuthServicesTest {
    @Test
    void failedLoginUsesGenericMessageAndPublishesNoSession() {
        var passwords = mock(PasswordLoginService.class);
        var refresh = mock(RefreshTokenService.class);
        var service = new AuthServiceImpl(passwords, mock(JwtUtil.class), mock(CookieUtils.class),
                mock(JpaUserRepository.class), mock(TokenRevocationService.class), refresh,
                mock(CompanyRegistrationService.class), mock(AuthAuditService.class));
        var request = LoginRequest.builder().email("unknown@example.com").password("wrong").build();
        assertThatThrownBy(() -> service.login(request, new org.springframework.mock.web.MockHttpServletResponse()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS)
                .hasMessage(ErrorCode.INVALID_CREDENTIALS.getDefaultMessage());
        verifyNoInteractions(refresh);
    }
}
