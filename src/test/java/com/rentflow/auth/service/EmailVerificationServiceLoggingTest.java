package com.rentflow.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.EmailVerificationTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceLoggingTest {

    @Mock private AuthUserRepository authUserRepository;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private EmailDeliveryService emailDeliveryService;

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUpLogger() {
        logger = (Logger) LoggerFactory.getLogger(EmailVerificationService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDownLogger() {
        logger.detachAppender(appender);
    }

    @Test
    void sendVerificationDoesNotLogRawToken() {
        AuthUser user = new AuthUser("alice@example.com", "hash", UserStatus.ACTIVE, false);
        user.setId(UUID.randomUUID());
        when(authUserRepository.findById(user.getId())).thenReturn(Optional.of(user));

        EmailVerificationService service = new EmailVerificationService(
                authUserRepository,
                emailVerificationTokenRepository,
                emailDeliveryService);

        service.sendVerification(user.getId());

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(msg -> msg.contains("/verify-email?token=") || msg.contains("token="));
    }
}
