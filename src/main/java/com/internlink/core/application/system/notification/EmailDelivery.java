package com.internlink.core.application.system.notification;

import java.util.UUID;

/** A leased outbox record, independent of the sending provider. */
public record EmailDelivery(
        UUID id,
        UUID notificationId,
        UUID userId,
        String email,
        String subject,
        String body,
        String htmlBody,
        String type,
        int attempts,
        UUID lease) {}
