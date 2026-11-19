package com.internlink.core.application.auth;

import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.GoogleTokenVerifier;
import com.internlink.core.infrastructure.security.GoogleTokenVerifier.GoogleUserInfo;
import com.internlink.core.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAccountLinkServiceTest {
    @Mock JpaUserRepository users;
    @Mock GoogleTokenVerifier verifier;
    GoogleAccountLinkService service;
    final UUID id = UUID.randomUUID();
    User user;

    @BeforeEach
    void setUp() {
        service = new GoogleAccountLinkService(users, verifier);
        user = new User();
        user.setEmail("sv@student.ctu.edu.vn");
        user.setIsActive(true);
        user.setMustChangePassword(false);
        when(users.findByIdForUpdate(id)).thenReturn(Optional.of(user));
    }

    @Test
    void linksWhenEmailMatches() {
        when(verifier.verify("t")).thenReturn(new GoogleUserInfo("sub-1", "SV@student.ctu.edu.vn", "SV", true));
        service.link(id, "t");
        assertThat(user.getGoogleSubject()).isEqualTo("sub-1");
        verify(users).saveAndFlush(user);
    }

    @Test
    void rejectsDifferentEmailWithHelpfulMessage() {
        when(verifier.verify("t")).thenReturn(new GoogleUserInfo("sub-1", "ca.nhan@gmail.com", "SV", true));
        assertThatThrownBy(() -> service.link(id, "t"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("không trùng").hasMessageContaining("sv@student.ctu.edu.vn");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsBeforeFirstPasswordChange() {
        user.setMustChangePassword(true);
        when(verifier.verify("t")).thenReturn(new GoogleUserInfo("sub-1", "sv@student.ctu.edu.vn", "SV", true));
        assertThatThrownBy(() -> service.link(id, "t")).hasMessageContaining("đổi mật khẩu");
    }

    @Test
    void unlinkClearsSubject() {
        user.setGoogleSubject("sub-1");
        service.unlink(id);
        assertThat(user.getGoogleSubject()).isNull();
        verify(users).saveAndFlush(user);
    }

    @Test
    void unlinkIsNoOpWhenNotLinked() {
        service.unlink(id);
        verify(users, never()).saveAndFlush(any());
    }
}
