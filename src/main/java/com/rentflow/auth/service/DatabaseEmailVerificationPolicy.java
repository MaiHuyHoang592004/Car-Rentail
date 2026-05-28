package com.rentflow.auth.service;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.common.exception.AuthenticationException;
import com.rentflow.common.exception.EmailNotVerifiedException;
import com.rentflow.common.security.EmailVerificationPolicy;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DatabaseEmailVerificationPolicy implements EmailVerificationPolicy {

    private final AuthUserRepository authUserRepository;

    public DatabaseEmailVerificationPolicy(AuthUserRepository authUserRepository) {
        this.authUserRepository = authUserRepository;
    }

    @Override
    public void requireVerifiedEmail(UUID userId) {
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(AuthenticationException::invalidCredentials);
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new EmailNotVerifiedException();
        }
    }
}
