package vn.id.fitnessai.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/** Shared From header for both plain-text and HTML mail. */
@Component
public class MailSenderIdentity {
    private final String fromHeader;

    public MailSenderIdentity(@Value("${spring.mail.username}") String address,
                              @Value("${app.mail.sender-name:FitnessAI Team}") String displayName) {
        try {
            InternetAddress from = new InternetAddress(address, displayName, StandardCharsets.UTF_8.name());
            from.validate();
            fromHeader = from.toString();
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Cấu hình người gửi email không hợp lệ", e);
        }
    }

    public void applyTo(SimpleMailMessage message) {
        message.setFrom(fromHeader);
    }

    public void applyTo(MimeMessageHelper helper) throws MessagingException {
        helper.setFrom(fromHeader);
    }
}
