package com.internlink.core.application.system.notification;

import com.internlink.core.infrastructure.integration.email.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "internlink.email.enabled", havingValue = "true")
public class EmailOutboxWorker {
    private final EmailOutboxStore store;
    private final EmailDeliveryGateway sender;
    private final EmailProperties settings;
    private final EmailRecipientPolicy recipients;

    @Scheduled(fixedDelayString = "${internlink.email.poll-interval-ms:15000}")
    public void tick() {
        store.expireLeases();
        for (int i = 0; i < settings.getBatchSize(); i++) {
            var row = claim();
            if (row == null) break;
            deliver(row);
        }
    }

    EmailDelivery claim() {
        return store.claim();
    }

    void deliver(EmailDelivery row) {
        if (!settings.accepts(row.email())) {
            finish(row, "SKIPPED", "environment_recipient_blocked", null, 0);
            return;
        }
        if (!recipients.allows(row)) {
            finish(row, "SKIPPED", "email_preference_or_recipient_changed", null, 0);
            return;
        }
        try {
            finish(
                    row,
                    "ACCEPTED",
                    null,
                    sender.deliver(
                            row.id(),
                            settings.routeRecipient(row.email()),
                            settings.routeSubject(row.email(), row.subject()),
                            row.body(),
                            row.htmlBody()),
                    0);
        } catch (EmailDeliveryException ex) {
            String result =
                    ex.ambiguous()
                            ? "UNKNOWN"
                            : ex.retryable() && row.attempts() < settings.getMaxAttempts()
                                    ? "RETRY"
                                    : "FAILED";
            finish(row, result, ex.code(), null, ex.retryAfterSeconds());
        } catch (RuntimeException ex) {
            finish(row, "UNKNOWN", "delivery_result_unknown", null, 0);
        }
    }

    private void finish(
            EmailDelivery row,
            String result,
            String error,
            String providerId,
            long requestedDelay) {
        long delay =
                Math.max(
                        Math.min(86400, requestedDelay),
                        Math.min(3600, 30L << Math.min(row.attempts(), 6)));
        if (store.finish(row, result, error, providerId, OffsetDateTime.now().plusSeconds(delay))
                && Set.of("FAILED", "UNKNOWN").contains(result))
            log.warn("Email delivery {} ended as {} ({})", row.id(), result, error);
    }
}
