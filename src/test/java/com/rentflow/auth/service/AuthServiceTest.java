package com.rentflow.auth.service;

import com.rentflow.auth.dto.LoginRequest;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.common.exception.AuthenticationException;
import com.rentflow.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthUserRepository authUserRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private AuthUserProfilePort userProfilePort;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginAttemptTracker loginAttemptTracker;

    @Test
    void loginUnknownEmailStillRunsPasswordHashCheck() {
        AuthService service = new AuthService(
                authUserRepository,
                userRoleRepository,
                userProfilePort,
                refreshTokenService,
                tokenProvider,
                passwordEncoder,
                loginAttemptTracker);
        LoginRequest request = new LoginRequest("missing@example.com", "Password@123");
        when(authUserRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(AuthenticationException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_INVALID_CREDENTIALS");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).matches(eq("Password@123"), hashCaptor.capture());
        assertThat(hashCaptor.getValue()).startsWith("$2a$12$");
    }
}
