package com.rentflow.auth.service;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.common.exception.EmailNotVerifiedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseEmailVerificationPolicyTest {

    @Mock private AuthUserRepository authUserRepository;

    @Test
    void requireVerifiedEmailPassesWhenUserIsVerified() {
        UUID userId = UUID.randomUUID();
        AuthUser user = new AuthUser("verified@example.com", "hash", UserStatus.ACTIVE, true);
        when(authUserRepository.findById(userId)).thenReturn(Optional.of(user));
        DatabaseEmailVerificationPolicy policy = new DatabaseEmailVerificationPolicy(authUserRepository);

        assertThatCode(() -> policy.requireVerifiedEmail(userId)).doesNotThrowAnyException();
    }

    @Test
    void requireVerifiedEmailThrowsWhenUserIsNotVerified() {
        UUID userId = UUID.randomUUID();
        AuthUser user = new AuthUser("unverified@example.com", "hash", UserStatus.ACTIVE, false);
        when(authUserRepository.findById(userId)).thenReturn(Optional.of(user));
        DatabaseEmailVerificationPolicy policy = new DatabaseEmailVerificationPolicy(authUserRepository);

        assertThatThrownBy(() -> policy.requireVerifiedEmail(userId))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_NOT_VERIFIED");
    }
}
