package com.internlink.core.application.system.notification;

import java.util.UUID;

public interface EmailDeliveryGateway {
    /** Gửi email; {@code html} có thể null, khi đó chỉ gửi bản text. */
    String deliver(UUID deliveryId, String recipient, String subject, String body, String html);
}
