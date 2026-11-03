package com.internlink.core.application.system.notification;

import com.internlink.core.domain.system.Notification;
import com.internlink.core.infrastructure.integration.email.EmailProperties;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmailOutboxService {
    private final EmailOutboxStore store;
    private final EmailProperties settings;
    private final EmailRecipientPolicy recipients;
    private final JpaUserRepository users;
    private final NotificationEmailComposer composer;

    @Transactional
    public String enqueue(Notification notification) {
        if (!canQueue()) return "DISABLED";
        if (!recipients.allows(notification.getRecipient(), notification.getNotificationType()))
            return "SKIPPED";
        return insert(
                        notification.getRecipient().getEmail(),
                        composer.render(notification),
                        "notification:" + notification.getId(),
                        notification.getId(),
                        notification.getRecipient().getId(),
                        notification.getNotificationType(),
                        EmailDispatchContext.none())
                ? "QUEUED"
                : "DUPLICATE";
    }

    @Transactional
    public boolean enqueueNotice(String email, RenderedEmail content) {
        return enqueueNotice(
                email,
                content,
                "ROSTER_NOTICE",
                "notice:" + digest(email + "\n" + content.subject() + "\n" + content.text()));
    }

    @Transactional
    public boolean enqueueNotice(String email, RenderedEmail content, String type, String key) {
        return enqueueNotice(email, content, type, key, EmailDispatchContext.none());
    }

    @Transactional
    public boolean enqueueNotice(
            String email,
            RenderedEmail content,
            String type,
            String key,
            EmailDispatchContext context) {
        if (!canQueue()) return false;
        var registered = users.findByEmail(email);
        if (registered.isPresent() && !recipients.allows(registered.get(), type)) return false;
        return insert(
                email,
                content,
                key,
                null,
                registered.map(user -> user.getId()).orElse(null),
                type,
                context == null ? EmailDispatchContext.none() : context);
    }

    private boolean insert(
            String email,
            RenderedEmail content,
            String key,
            UUID notificationId,
            UUID userId,
            String type,
            EmailDispatchContext context) {
        String subject = content == null ? null : content.subject();
        if (email == null
                || email.length() > 254
                || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                || subject == null
                || subject.isBlank()
                || subject.length() > 255
                || subject.contains("\r")
                || subject.contains("\n")
                || content.text() == null
                || content.text().length() > 50000
                || (content.html() != null && content.html().length() > 200000)
                || key == null
                || key.length() > 200)
            throw new IllegalArgumentException("Nội dung email không hợp lệ");
        return store.insert(email, content, key, notificationId, userId, type, context);
    }

    public void attachContext(UUID notificationId, EmailDispatchContext context) {
        if (notificationId != null && context != null) store.attachContext(notificationId, context);
    }

    private boolean canQueue() {
        return settings.isEnabled() && !"DISABLED".equals(settings.getDeliveryMode());
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
