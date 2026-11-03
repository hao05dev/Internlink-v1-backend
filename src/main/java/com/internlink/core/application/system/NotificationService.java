package com.internlink.core.application.system;

import com.internlink.core.presentation.system.dto.response.NotificationResponse;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    UUID publishNotification(
            UUID recipientId,
            String type,
            String title,
            String message,
            String actionUrl,
            boolean email);

    com.internlink.core.presentation.system.dto.response.NotificationPageResponse getPage(
            UUID recipientId, int page, int size, boolean unread);

    List<NotificationResponse> getMyNotifications(UUID recipientId);

    NotificationResponse getById(UUID id, UUID recipientId);

    long countUnreadNotifications(UUID recipientId);

    NotificationResponse markAsRead(UUID id, UUID recipientId);

    void markAllAsRead(UUID recipientId);

    /** Creates an in-app notification. Email must be requested explicitly via publishNotification. */
    void sendNotification(
            UUID recipientId, String type, String title, String message, String actionUrl);

    default void sendInAppNotification(
            UUID recipientId, String type, String title, String message, String actionUrl) {
        sendNotification(recipientId, type, title, message, actionUrl);
    }
}
