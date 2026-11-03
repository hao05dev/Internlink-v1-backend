package com.internlink.core.presentation.system.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** HTTP representations of the email operations workspace. No MIME credentials are exposed. */
public final class EmailDeliveryResponse {
    private EmailDeliveryResponse() {}

    public record Item(
            UUID id,
            UUID notificationId,
            String recipient,
            String subject,
            String status,
            int attempts,
            String errorCode,
            String providerMessageId,
            String templateKey,
            UUID initiatedBy,
            String sourceType,
            UUID sourceId,
            String scopeType,
            UUID scopeId,
            OffsetDateTime createdAt,
            OffsetDateTime nextAttemptAt,
            OffsetDateTime acceptedAt,
            OffsetDateTime deliveredAt,
            OffsetDateTime bouncedAt) {}

    public record Page(List<Item> items, long totalElements, int totalPages) {}

    public record Controls(boolean paused, int dailyLimit, long usedLast24Hours) {}

    public record Metrics(Long oldestPendingSeconds, Double averageAcceptanceSeconds24Hours) {}

    public record Summary(
            boolean enabled,
            String provider,
            Map<String, Long> counts,
            Controls controls,
            Metrics metrics) {}
}
