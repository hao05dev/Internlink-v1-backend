package com.internlink.core.application.system.notification;

import com.internlink.core.domain.system.Notification;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationEmailComposer {
    public static final String VERSION = "notification-html-v1";
    private final EmailRenderer renderer;

    public RenderedEmail render(Notification notification) {
        var role = notification.getRecipient().getRole();
        String path = NotificationLinks.normalize(notification.getActionUrl(), role);
        String home = NotificationLinks.home(role);
        String preferences = home.substring(0, home.indexOf('/', 1)) + "/notifications";
        String title = notification.getTitle();
        if (title.startsWith("[InternLink]")) title = title.substring("[InternLink]".length()).strip();
        return renderer.render(
                EmailTemplate.NOTIFICATION,
                Map.of(
                        "title", title,
                        "message", notification.getMessage() == null ? "" : notification.getMessage(),
                        "actionUrl", renderer.baseUrl() + path,
                        "preferencesUrl", renderer.baseUrl() + preferences));
    }
}
