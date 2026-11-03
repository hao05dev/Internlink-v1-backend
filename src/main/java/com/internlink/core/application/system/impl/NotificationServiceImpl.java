package com.internlink.core.application.system.impl;

import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.system.Notification;
import com.internlink.core.infrastructure.persistence.jpa.JpaNotificationRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.system.dto.response.NotificationResponse;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl
        implements NotificationService, org.springframework.context.ApplicationEventPublisherAware {

    private final JpaNotificationRepository notificationRepository;
    private final JpaUserRepository userRepository;
    private final com.internlink.core.application.system.notification.EmailOutboxService
            emailOutbox;
    private org.springframework.context.ApplicationEventPublisher events;

    @Override
    public void setApplicationEventPublisher(
            org.springframework.context.ApplicationEventPublisher publisher) {
        this.events = publisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(UUID recipientId) {
        return notificationRepository
                .findTop50ByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getById(UUID id, UUID recipientId) {
        return mapToResponse(findOwned(id, recipientId, "Bạn không có quyền xem thông báo này"));
    }

    private Notification findOwned(UUID id, UUID recipientId, String forbiddenMessage) {
        Notification notification =
                notificationRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
        if (!notification.getRecipient().getId().equals(recipientId)) {
            throw new ForbiddenException(forbiddenMessage);
        }
        return notification;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnreadNotifications(UUID recipientId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID id, UUID recipientId) {
        Notification notification =
                findOwned(id, recipientId, "Bạn không có quyền cập nhật thông báo này");

        if (Boolean.TRUE.equals(notification.getIsRead())) return mapToResponse(notification);
        notification.setIsRead(true);
        notification.setReadAt(OffsetDateTime.now());

        var saved = notificationRepository.save(notification);
        if (events != null)
            events.publishEvent(
                    new com.internlink.core.application.system.notification.NotificationChanged(
                            recipientId));
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID recipientId) {
        notificationRepository.markAllAsRead(recipientId);
        if (events != null)
            events.publishEvent(
                    new com.internlink.core.application.system.notification.NotificationChanged(
                            recipientId));
    }

    @Override
    @Transactional
    public void sendNotification(
            UUID recipientId, String type, String title, String message, String actionUrl) {
        create(recipientId, type, title, message, actionUrl, false);
    }

    @Override
    @Transactional
    public void sendInAppNotification(
            UUID recipientId, String type, String title, String message, String actionUrl) {
        create(recipientId, type, title, message, actionUrl, false);
    }

    @Override
    @Transactional
    public UUID publishNotification(
            UUID recipientId,
            String type,
            String title,
            String message,
            String actionUrl,
            boolean email) {
        return create(recipientId, type, title, message, actionUrl, email);
    }

    private UUID create(
            UUID recipientId,
            String type,
            String title,
            String message,
            String actionUrl,
            boolean email) {
        User recipient =
                userRepository
                        .findById(recipientId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("User", "id", recipientId));

        if (!Boolean.TRUE.equals(recipient.getIsActive())) return null;
        if (type == null
                || !type.matches("[A-Z0-9_]{1,50}")
                || title == null
                || title.isBlank()
                || title.length() > 255
                || message == null
                || message.length() > 50000)
            throw new IllegalArgumentException("Nội dung thông báo không hợp lệ");
        Notification notification =
                Notification.builder()
                        .recipient(recipient)
                        .notificationType(type)
                        .title(title)
                        .message(message)
                        .actionUrl(
                                com.internlink.core.application.system.notification
                                        .NotificationLinks.normalize(
                                        actionUrl, recipient.getRole()))
                        .isRead(false)
                        .build();

        // JdbcTemplate cannot flush Hibernate's pending INSERT before checking the outbox FK.
        notificationRepository.saveAndFlush(notification);
        String status = email ? emailOutbox.enqueue(notification) : "SEPARATE_NOTICE";
        notification.setDeliveryChannels(
                java.util.Map.of("inApp", true, "email", "QUEUED".equals(status)));
        notification.setDeliveryStatus(
                java.util.Map.of(
                        "inApp",
                        "DELIVERED",
                        "email",
                        status,
                        "templateVersion",
                        com.internlink.core.application.system.notification
                                .NotificationEmailComposer.VERSION));
        notificationRepository.save(notification);
        if (events != null)
            events.publishEvent(
                    new com.internlink.core.application.system.notification.NotificationChanged(
                            recipientId));
        return notification.getId();
    }

    @Transactional(readOnly = true)
    public com.internlink.core.presentation.system.dto.response.NotificationPageResponse getPage(
            UUID recipientId, int page, int size, boolean unread) {
        var paging =
                org.springframework.data.domain.PageRequest.of(
                        Math.max(0, page),
                        Math.max(1, Math.min(100, size)),
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.desc("createdAt"),
                                org.springframework.data.domain.Sort.Order.desc("id")));
        var rows =
                unread
                        ? notificationRepository.findByRecipientIdAndIsReadFalse(
                                recipientId, paging)
                        : notificationRepository.findByRecipientId(recipientId, paging);
        return new com.internlink.core.presentation.system.dto.response.NotificationPageResponse(
                rows.getContent().stream().map(this::mapToResponse).toList(),
                rows.getTotalElements(),
                rows.getTotalPages(),
                notificationRepository.countByRecipientIdAndIsReadFalse(recipientId));
    }

    private NotificationResponse mapToResponse(Notification entity) {
        return NotificationResponse.builder()
                .id(entity.getId())
                .recipientUserId(entity.getRecipient().getId())
                .notificationType(entity.getNotificationType())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .actionUrl(entity.getActionUrl())
                .isRead(entity.getIsRead())
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .category(
                        com.internlink.core.application.system.notification.NotificationPolicy
                                .category(entity.getNotificationType())
                                .name())
                .deliveryStatus(entity.getDeliveryStatus())
                .build();
    }
}
