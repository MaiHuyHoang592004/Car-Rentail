package com.rentflow.auth.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

@Slf4j
@Service
public class SmtpEmailDeliveryService implements EmailDeliveryService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String from;
    private final String frontendBaseUrl;
    private final String mailHost;
    private final int mailPort;

    public SmtpEmailDeliveryService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${rentflow.mail.enabled:false}") boolean enabled,
            @Value("${rentflow.mail.from:no-reply@rentflow.local}") String from,
            @Value("${rentflow.mail.frontend-base-url:http://localhost:3002}") String frontendBaseUrl,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.port:587}") int mailPort) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
        this.mailHost = mailHost;
        this.mailPort = mailPort;
    }

    @PostConstruct
    void logMailDeliveryConfig() {
        log.info("Mail delivery config: enabled={}, host={}, port={}, from={}",
                enabled,
                mailHost == null || mailHost.isBlank() ? "<unset>" : mailHost,
                mailPort,
                from);
    }

    @Override
    public void sendVerificationEmail(String to, String rawToken, Instant expiresAt) {
        if (!enabled) {
            log.info("[email-disabled] verification email not sent for {}", to);
            return;
        }

        if (mailHost == null || mailHost.isBlank()) {
            throw new EmailDeliveryException("Mail sender host is not configured");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new EmailDeliveryException("Mail sender is not configured");
        }

        String verificationUrl = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/verify-email")
                .queryParam("token", rawToken)
                .build()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Xác minh email RentFlow");
        message.setText("""
                Xin chào,

                Vui lòng xác minh email RentFlow bằng liên kết sau:
                %s

                Liên kết có hiệu lực đến: %s.
                Nếu bạn không tạo tài khoản RentFlow, vui lòng bỏ qua email này.

                RentFlow
                """.formatted(verificationUrl, expiresAt));

        try {
            mailSender.send(message);
            log.info("Verification email sent to {}", to);
        } catch (MailException ex) {
            throw new EmailDeliveryException("Failed to send verification email", ex);
        }
    }
}
