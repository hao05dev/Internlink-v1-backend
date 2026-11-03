package com.internlink.core.infrastructure.integration.google;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internlink.core.infrastructure.integration.email.*;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.io.*;
import java.util.*;

class GmailSenderTest {
    GoogleWorkspaceClient google;
    GmailSender sender;
    EmailProperties settings;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        google = mock(GoogleWorkspaceClient.class);
        settings = new EmailProperties();
        settings.setEnabled(true);
        settings.setProvider("GOOGLE_WORKSPACE");
        settings.setFrom("office@example.edu");
        sender =
                new GmailSender(
                        settings,
                        mock(GmailSmtpSender.class),
                        new GmailApiSender(google, mapper, settings, new EmailMessageFactory()));
    }

    @Test
    void workspaceUsesOnlySendScopeAndPreservesVietnameseMime() throws Exception {
        when(google.json(anyString(), any(), anyString(), anyString(), any(), anyString()))
                .thenReturn(mapper.readTree("{\"id\":\"gmail-123\"}"));
        UUID id = UUID.randomUUID();
        assertThat(
                        sender.deliver(
                                id,
                                "student@example.edu",
                                "Kết quả thực tập",
                                "Hồ sơ đã được duyệt", null))
                .isEqualTo("gmail-123");
        var payload = ArgumentCaptor.forClass(byte[].class);
        verify(google)
                .json(
                        eq("POST"),
                        any(),
                        eq(GoogleWorkspaceClient.GMAIL_SEND),
                        eq("office@example.edu"),
                        payload.capture(),
                        eq("application/json"));
        byte[] raw =
                Base64.getUrlDecoder()
                        .decode(mapper.readTree(payload.getValue()).get("raw").asText());
        var message =
                new MimeMessage(
                        Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
        assertThat(message.getSubject()).isEqualTo("Kết quả thực tập");
        assertThat(message.getContent()).isEqualTo("Hồ sơ đã được duyệt");
        assertThat(message.getMessageID()).isEqualTo("<" + id + "@example.edu>");
    }

    @Test
    void headerInjectionIsRejectedWithoutNetwork() {
        assertThatThrownBy(
                        () ->
                                sender.deliver(
                                        UUID.randomUUID(),
                                        "student@example.edu\r\nBcc: attacker@example.com",
                                        "Subject",
                                        "Body", null))
                .isInstanceOf(EmailDeliveryException.class);
        assertThatThrownBy(
                        () ->
                                sender.deliver(
                                        UUID.randomUUID(),
                                        "student@example.edu",
                                        "Subject\r\nBcc: attacker@example.com",
                                        "Body", null))
                .isInstanceOf(EmailDeliveryException.class);
        verifyNoInteractions(google);
    }

    @Test
    void explicitTemporaryRejectionIsRetryable() {
        when(google.json(anyString(), any(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new GoogleWorkspaceClient.GoogleApiException(429));
        var error =
                catchThrowableOfType(
                        () ->
                                sender.deliver(
                                        UUID.randomUUID(),
                                        "student@example.edu",
                                        "Subject",
                                        "Body", null),
                        EmailDeliveryException.class);
        assertThat(error.retryable()).isTrue();
        assertThat(error.ambiguous()).isFalse();
    }

    @Test
    void unconfirmedNetworkResultDoesNotRetryBlindly() {
        when(google.json(anyString(), any(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("network timeout"));
        var error =
                catchThrowableOfType(
                        () ->
                                sender.deliver(
                                        UUID.randomUUID(),
                                        "student@example.edu",
                                        "Subject",
                                        "Body", null),
                        EmailDeliveryException.class);
        assertThat(error.ambiguous()).isTrue();
        assertThat(error.retryable()).isFalse();
    }

    @Test
    void disabledEmailCannotSend() {
        settings.setEnabled(false);
        assertThatThrownBy(
                        () ->
                                sender.deliver(
                                        UUID.randomUUID(),
                                        "student@example.edu",
                                        "Subject",
                                        "Body", null))
                .isInstanceOf(EmailDeliveryException.class);
        verifyNoInteractions(google);
    }

    @Test
    void tokenRejectionCannotBeMistakenForSubmittedEmail() {
        when(google.json(anyString(), any(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new GoogleWorkspaceClient.GoogleAuthenticationException(false));
        var error =
                catchThrowableOfType(
                        () ->
                                sender.deliver(
                                        UUID.randomUUID(),
                                        "student@example.edu",
                                        "Subject",
                                        "Body", null),
                        EmailDeliveryException.class);
        assertThat(error.ambiguous()).isFalse();
        assertThat(error.retryable()).isFalse();
    }

    @Test
    void smtpConfigurationRequiresMatchingSenderAndAppPassword() {
        settings.setProvider("GMAIL_SMTP");
        settings.setDeliveryMode("ALLOWLIST");
        settings.setAllowedRecipients(Set.of("student@example.edu"));
        assertThatThrownBy(settings::validate).isInstanceOf(IllegalStateException.class);
        settings.setSmtpUsername(settings.getFrom());
        settings.setSmtpAppPassword("test-only-password");
        settings.setPublicBaseUrl("https://app.example.edu");
        assertThatCode(settings::validate).doesNotThrowAnyException();
        settings.setPublicBaseUrl("https://internlink.example@evil.example/path");
        assertThatThrownBy(settings::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void quota403UsesRetryAfterButPermission403DoesNotRetry() {
        when(google.json(anyString(), any(), anyString(), anyString(), any(), anyString()))
                .thenThrow(
                        new GoogleWorkspaceClient.GoogleApiException(
                                403, "userRateLimitExceeded", 180));
        var quota =
                catchThrowableOfType(
                        () ->
                                sender.deliver(
                                        UUID.randomUUID(),
                                        "student@example.edu",
                                        "Subject",
                                        "Body", null),
                        EmailDeliveryException.class);
        assertThat(quota.retryable()).isTrue();
        assertThat(quota.retryAfterSeconds()).isEqualTo(180);
        doThrow(new GoogleWorkspaceClient.GoogleApiException(403, "domainPolicy", 0))
                .when(google)
                .json(anyString(), any(), anyString(), anyString(), any(), anyString());
        var permission =
                catchThrowableOfType(
                        () ->
                                sender.deliver(
                                        UUID.randomUUID(),
                                        "student@example.edu",
                                        "Subject",
                                        "Body", null),
                        EmailDeliveryException.class);
        assertThat(permission.retryable()).isFalse();
        assertThat(permission.ambiguous()).isFalse();
    }
}
