package com.internlink.core.infrastructure.integration.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internlink.core.infrastructure.integration.email.*;

import jakarta.mail.Session;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.*;

@Component
@RequiredArgsConstructor
public class GmailApiSender {
    private final GoogleWorkspaceClient google;
    private final ObjectMapper mapper;
    private final EmailProperties settings;
    private final EmailMessageFactory messages;

    public String deliver(UUID id, String email, String subject, String body, String html) {
        var message =
                messages.create(
                        Session.getInstance(new Properties()),
                        id,
                        settings.getFrom(),
                        email,
                        subject,
                        body,
                        html);
        try {
            var bytes = new ByteArrayOutputStream();
            message.writeTo(bytes);
            var result =
                    google.json(
                            "POST",
                            URI.create(
                                    "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"),
                            GoogleWorkspaceClient.GMAIL_SEND,
                            settings.getFrom(),
                            mapper.writeValueAsBytes(
                                    Map.of(
                                            "raw",
                                            Base64.getUrlEncoder()
                                                    .withoutPadding()
                                                    .encodeToString(bytes.toByteArray()))),
                            "application/json");
            if (!result.hasNonNull("id") || result.get("id").asText().isBlank())
                throw new EmailDeliveryException("gmail_result_unknown", false, true);
            return result.get("id").asText();
        } catch (EmailDeliveryException ex) {
            throw ex;
        } catch (GoogleWorkspaceClient.GoogleAuthenticationException ex) {
            throw new EmailDeliveryException("google_authentication_failed", ex.retryable(), false);
        } catch (GoogleWorkspaceClient.GoogleApiException ex) {
            boolean retry =
                    ex.status() == 429
                            || ex.status() >= 500
                            || ex.status() == 403
                                    && Set.of(
                                                    "rateLimitExceeded",
                                                    "userRateLimitExceeded",
                                                    "dailyLimitExceeded")
                                            .contains(ex.reason());
            throw new EmailDeliveryException(
                    "gmail_http_" + ex.status(), retry, false, ex.retryAfterSeconds());
        } catch (Exception ex) {
            throw new EmailDeliveryException("gmail_result_unknown", false, true);
        }
    }
}
