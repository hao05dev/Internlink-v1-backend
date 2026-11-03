package com.internlink.core.infrastructure.integration.email;

import jakarta.mail.*;
import jakarta.mail.internet.*;

import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.*;

@Component
public class EmailMessageFactory {
    public MimeMessage create(
            Session session, UUID id, String from, String to, String subject, String body) {
        return create(session, id, from, to, subject, body, null);
    }

    /** Có {@code html} thì gửi multipart/alternative (text + HTML), không thì chỉ gửi text. */
    public MimeMessage create(
            Session session,
            UUID id,
            String from,
            String to,
            String subject,
            String body,
            String html) {
        try {
            if (subject == null
                    || subject.isBlank()
                    || subject.contains("\r")
                    || subject.contains("\n")
                    || body == null)
                throw new EmailDeliveryException("invalid_email_content", false, false);
            MimeMessage message =
                    new MimeMessage(session) {
                        @Override
                        protected void updateMessageID() throws MessagingException {
                            setHeader("Message-ID", "<" + id + "@" + senderDomain(from) + ">");
                        }
                    };
            InternetAddress sender = address(from);
            sender.setPersonal("InternLink", "UTF-8");
            message.setFrom(sender);
            message.setReplyTo(new Address[] {sender});
            message.setRecipient(Message.RecipientType.TO, address(to));
            message.setSubject(subject, "UTF-8");
            if (html == null || html.isBlank()) {
                message.setHeader("X-InternLink-Template", "plain-v1");
                message.setText(body, "UTF-8");
            } else {
                message.setHeader("X-InternLink-Template", "html-v1");
                var text = new MimeBodyPart();
                text.setText(body, "UTF-8");
                var rich = new MimeBodyPart();
                rich.setContent(html, "text/html; charset=UTF-8");
                var alternative = new MimeMultipart("alternative");
                alternative.addBodyPart(text);
                alternative.addBodyPart(rich);
                message.setContent(alternative);
            }
            message.setSentDate(new Date());
            message.saveChanges();
            return message;
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new EmailDeliveryException("email_address_or_content_invalid", false, false);
        }
    }

    private InternetAddress address(String value) throws AddressException {
        if (value == null || value.contains("\r") || value.contains("\n"))
            throw new AddressException("invalid address");
        var address = new InternetAddress(value, true);
        address.validate();
        return address;
    }

    private String senderDomain(String from) {
        int marker = from == null ? -1 : from.lastIndexOf('@');
        return marker < 0 ? "invalid.local" : from.substring(marker + 1).toLowerCase(Locale.ROOT);
    }
}
