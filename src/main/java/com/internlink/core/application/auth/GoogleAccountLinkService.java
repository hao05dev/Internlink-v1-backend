package com.internlink.core.application.auth;

import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.GoogleTokenVerifier;
import com.internlink.core.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleAccountLinkService {
    private final JpaUserRepository users;
    private final GoogleTokenVerifier verifier;

    @Transactional
    public void link(UUID userId, String token) {
        var identity = verifier.verify(token);
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new UnauthorizedException("Tài khoản không hợp lệ"));
        if (!identity.emailVerified() || !identity.email().equalsIgnoreCase(user.getEmail())) {
            throw new UnauthorizedException("Email Google (" + identity.email()
                    + ") không trùng với email tài khoản. Hãy chọn đúng tài khoản " + user.getEmail() + ".");
        }
        if (!Boolean.TRUE.equals(user.getIsActive()) || Boolean.TRUE.equals(user.getMustChangePassword())) {
            throw new UnauthorizedException("Hãy đổi mật khẩu lần đầu trước khi liên kết Google.");
        }
        if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(identity.sub())) {
            throw new UnauthorizedException("Tài khoản đã liên kết với một danh tính Google khác. Hãy huỷ liên kết trước.");
        }
        user.setGoogleSubject(identity.sub());
        user.setEmailVerifiedAt(OffsetDateTime.now());
        users.saveAndFlush(user);
    }

    @Transactional
    public void unlink(UUID userId) {
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new UnauthorizedException("Tài khoản không hợp lệ"));
        if (user.getGoogleSubject() == null) return;
        user.setGoogleSubject(null);
        users.saveAndFlush(user);
    }
}
