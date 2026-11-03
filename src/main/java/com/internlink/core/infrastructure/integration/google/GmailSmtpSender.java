package com.internlink.core.infrastructure.integration.google;

import com.internlink.core.infrastructure.integration.email.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GmailSmtpSender {
    private final EmailProperties settings;
    private final EmailMessageFactory messages;
    private final JavaMailSenderImpl client;

    @Autowired
    public GmailSmtpSender(EmailProperties settings, EmailMessageFactory messages) {
        this(settings, messages, new JavaMailSenderImpl());
        client.setHost("smtp.gmail.com");
        client.setPort(587);
        client.setUsername(settings.getSmtpUsername());
        client.setPassword(settings.getSmtpAppPassword());
        var props = client.getJavaMailProperties();
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.starttls.enable", "true");
        props.setProperty("mail.smtp.starttls.required", "true");
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        props.setProperty("mail.smtp.connectiontimeout", "5000");
        props.setProperty("mail.smtp.timeout", "20000");
        props.setProperty("mail.smtp.writetimeout", "20000");
    }

    GmailSmtpSender(
            EmailProperties settings, EmailMessageFactory messages, JavaMailSenderImpl client) {
        this.settings = settings;
        this.messages = messages;
        this.client = client;
    }

    public String deliver(UUID id, String email, String subject, String body, String html) {
        if (!settings.getFrom().equalsIgnoreCase(settings.getSmtpUsername())
                || settings.getSmtpAppPassword().isBlank())
            throw new EmailDeliveryException("smtp_configuration_missing", false, false);
        var message =
                messages.create(
                        client.getSession(), id, settings.getFrom(), email, subject, body, html);
        try {
            client.send(message);
            return message.getMessageID();
        } catch (MailAuthenticationException ex) {
            throw new EmailDeliveryException("smtp_authentication_failed", false, false);
        } catch (MailException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause())
                if (cause instanceof java.net.ConnectException)
                    throw new EmailDeliveryException("smtp_connect_failed", true, false);
            throw new EmailDeliveryException("smtp_result_unknown", false, true);
        } catch (Exception ex) {
            throw new EmailDeliveryException("smtp_result_unknown", false, true);
        }
    }
}
