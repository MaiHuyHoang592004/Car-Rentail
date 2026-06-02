package com.rentflow.auth.service;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.EmailVerificationToken;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.EmailVerificationTokenRepository;
import com.rentflow.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private AuthUserRepository authUserRepository;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private EmailDeliveryService emailDeliveryService;

    @Test
    void sendVerificationPersistsHashAndSendsRawTokenOnlyToDeliveryService() {
        AuthUser user = user();
        when(authUserRepository.findById(user.getId())).thenReturn(Optional.of(user));

        EmailVerificationService service = new EmailVerificationService(
                authUserRepository,
                emailVerificationTokenRepository,
                emailDeliveryService);

        service.sendVerification(user.getId());

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);

        verify(emailVerificationTokenRepository).save(tokenCaptor.capture());
        verify(emailDeliveryService).sendVerificationEmail(
                eq(user.getEmail()),
                rawTokenCaptor.capture(),
                any(Instant.class));

        String rawToken = rawTokenCaptor.getValue();
        EmailVerificationToken saved = tokenCaptor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(PasswordService.sha256(rawToken));
        assertThat(saved.getTokenHash()).doesNotContain(rawToken);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void sendVerificationMapsDeliveryFailureToBusinessCode() {
        AuthUser user = user();
        when(authUserRepository.findById(user.getId())).thenReturn(Optional.of(user));
        doThrow(new EmailDeliveryException("SMTP unavailable"))
                .when(emailDeliveryService)
                .sendVerificationEmail(eq(user.getEmail()), any(String.class), any(Instant.class));

        EmailVerificationService service = new EmailVerificationService(
                authUserRepository,
                emailVerificationTokenRepository,
                emailDeliveryService);

        assertThatThrownBy(() -> service.sendVerification(user.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_DELIVERY_FAILED");
    }

    private AuthUser user() {
        AuthUser user = new AuthUser("alice@example.com", "hash", UserStatus.ACTIVE, false);
        user.setId(UUID.randomUUID());
        return user;
    }
}
