package com.internlink.core.infrastructure.integration.email;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;

import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EmailMessageFactoryTest {

    @Test
    void createsBrandedMessageWithSenderDomainMessageId() throws Exception {
        UUID id = UUID.randomUUID();

        var message =
                new EmailMessageFactory()
                        .create(
                                Session.getInstance(new Properties()),
                                id,
                                "mailer@internlink.edu.vn",
                                "student@example.com",
                                "Mời phỏng vấn",
                                "Nội dung");

        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getPersonal()).isEqualTo("InternLink");
        assertThat(message.getReplyTo()).containsExactly(message.getFrom());
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("student@example.com");
        assertThat(message.getMessageID()).isEqualTo("<" + id + "@internlink.edu.vn>");
        assertThat(message.getContentType()).startsWith("text/plain");
    }

    @Test
    void createsMultipartAlternativeWhenHtmlIsPresent() throws Exception {
        var message =
                new EmailMessageFactory()
                        .create(
                                Session.getInstance(new Properties()),
                                UUID.randomUUID(),
                                "mailer@internlink.edu.vn",
                                "student@example.com",
                                "Mời phỏng vấn",
                                "Nội dung",
                                "<p>Nội dung</p>");
        message.saveChanges();

        var multipart = (MimeMultipart) message.getContent();
        assertThat(multipart.getContentType()).startsWith("multipart/alternative");
        assertThat(multipart.getCount()).isEqualTo(2);
        assertThat(multipart.getBodyPart(0).getContentType()).startsWith("text/plain");
        assertThat(multipart.getBodyPart(1).getContentType()).startsWith("text/html");
        assertThat(message.getHeader("X-InternLink-Template")).containsExactly("html-v1");
    }
}
