package com.internlink.core.infrastructure.integration.google;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.internlink.core.infrastructure.integration.email.*;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.*;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.UUID;

class GmailSmtpSenderTest {
    JavaMailSenderImpl client = mock(JavaMailSenderImpl.class);
    GmailSmtpSender sender;

    @BeforeEach
    void setup() {
        var settings = new EmailProperties();
        settings.setFrom("test@example.edu");
        settings.setSmtpUsername(settings.getFrom());
        settings.setSmtpAppPassword("test-only");
        when(client.getSession())
                .thenReturn(jakarta.mail.Session.getInstance(new java.util.Properties()));
        sender = new GmailSmtpSender(settings, new EmailMessageFactory(), client);
    }

    @Test
    void authenticationFailureIsPermanent() {
        doThrow(new MailAuthenticationException("invalid"))
                .when(client)
                .send(any(MimeMessage.class));
        var error =
                catchThrowableOfType(
                        () ->
                                sender.deliver(
                                        UUID.randomUUID(),
                                        "user@example.edu",
                                        "Tiêu đề",
                                        "Nội dung", null),
                        EmailDeliveryException.class);
        assertThat(error.retryable()).isFalse();
        assertThat(error.ambiguous()).isFalse();
    }

    @Test
    void lostResultRequiresReconciliation() {
        doThrow(new MailSendException("timeout")).when(client).send(any(MimeMessage.class));
        var error =
                catchThrowableOfType(
                        () ->
                                sender.deliver(
                                        UUID.randomUUID(),
                                        "user@example.edu",
                                        "Tiêu đề",
                                        "Nội dung", null),
                        EmailDeliveryException.class);
        assertThat(error.ambiguous()).isTrue();
    }

    @Test
    void preConnectionFailureCanRetry() {
        doThrow(new MailSendException("connect", new java.net.ConnectException()))
                .when(client)
                .send(any(MimeMessage.class));
        var error =
                catchThrowableOfType(
                        () ->
                                sender.deliver(
                                        UUID.randomUUID(),
                                        "user@example.edu",
                                        "Tiêu đề",
                                        "Nội dung", null),
                        EmailDeliveryException.class);
        assertThat(error.retryable()).isTrue();
        assertThat(error.ambiguous()).isFalse();
    }
}
