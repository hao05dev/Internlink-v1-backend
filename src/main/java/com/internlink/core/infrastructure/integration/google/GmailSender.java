package com.internlink.core.infrastructure.integration.google;

import com.internlink.core.application.system.notification.EmailDeliveryGateway;
import com.internlink.core.infrastructure.integration.email.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GmailSender implements EmailDeliveryGateway {
    private final EmailProperties settings;
    private final GmailSmtpSender smtp;
    private final GmailApiSender api;

    @Override
    public String deliver(UUID id, String email, String subject, String body, String html) {
        if (!settings.isEnabled()) throw new EmailDeliveryException("email_disabled", false, false);
        return switch (settings.getProvider()) {
            case "GMAIL_SMTP" -> smtp.deliver(id, email, subject, body, html);
            case "GOOGLE_WORKSPACE" -> api.deliver(id, email, subject, body, html);
            default ->
                    throw new EmailDeliveryException("email_provider_not_configured", false, false);
        };
    }
}
