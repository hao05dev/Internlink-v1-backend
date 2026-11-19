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

    /** @param departmentViewerId null = toàn hệ thống; khác null = chỉ email thuộc khoa của người xem. */
    Map<String, Long> summary(java.util.UUID departmentViewerId);

    EmailDeliveryResponse.Metrics metrics(java.util.UUID departmentViewerId);

    default Map<String, Long> summary() {
        return summary(null);
    }

    default EmailDeliveryResponse.Metrics metrics() {
        return metrics(null);
    }

    EmailDeliveryResponse.Page page(EmailDeliveryFilter filter, int page, int size);

    /** Email {@code id} có thuộc khoa của người xem không. */
    boolean visibleTo(java.util.UUID id, java.util.UUID departmentViewerId);

    EmailDeliveryResponse.Controls controls();

    void updateControls(boolean paused, int limit);

    boolean retry(UUID id, boolean confirmedNotAccepted);

    boolean cancel(UUID id);

    void attachContext(UUID notificationId, EmailDispatchContext context);
}
