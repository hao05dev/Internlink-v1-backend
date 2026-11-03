package com.internlink.core.application.system.notification;

import com.internlink.core.presentation.system.dto.response.EmailDeliveryResponse;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public interface EmailOutboxStore {
    boolean insert(
            String email,
            RenderedEmail content,
            String key,
            UUID notificationId,
            UUID userId,
            String type,
            EmailDispatchContext context);

    EmailDelivery claim();

    void expireLeases();

    boolean finish(
            EmailDelivery row, String result, String error, String providerId, OffsetDateTime next);

    Map<String, Long> summary();

    EmailDeliveryResponse.Metrics metrics();

    EmailDeliveryResponse.Page page(EmailDeliveryFilter filter, int page, int size);

    EmailDeliveryResponse.Controls controls();

    void updateControls(boolean paused, int limit);

    boolean retry(UUID id, boolean confirmedNotAccepted);

    boolean cancel(UUID id);

    void attachContext(UUID notificationId, EmailDispatchContext context);
}
