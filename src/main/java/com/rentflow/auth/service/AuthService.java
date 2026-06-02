package com.rentflow.auth.service;

import com.rentflow.auth.dto.*;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.RefreshToken;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.common.exception.AccountLockedException;
import com.rentflow.common.exception.AccountSuspendedException;
import com.rentflow.common.exception.AuthenticationException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$lK6tMiUUcbddgmGqvuIrNO7KjBgKplGHbP1wLjTvj2GTl2zQLTlI.";
    // Threshold sits below the per-IP rate limit (5) so the per-account lock
    // kicks in even when an attacker rotates source IPs to bypass rate-limiting.
    private static final int LOCKOUT_THRESHOLD = 3;
    private static final java.time.Duration LOCKOUT_DURATION = java.time.Duration.ofMinutes(15);

    private final AuthUserRepository authUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthUserProfilePort userProfilePort;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptTracker loginAttemptTracker;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (authUserRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("USER_EMAIL_EXISTS", "Email is already registered");
        }

        List<Role> roles = resolveRoles(request.roles());
        if (roles.isEmpty()) {
            roles = List.of(Role.CUSTOMER);
        }

        AuthUser user = new AuthUser(
                request.email(),
                passwordEncoder.encode(request.password()),
                UserStatus.ACTIVE,
                false
        );

        user = authUserRepository.save(user);

        for (Role role : roles) {
            user.getRoles().add(new UserRole(user, role));
        }
        user = authUserRepository.save(user);

        log.info("User registered: {} with roles {}", user.getEmail(), roles);

        RegisterResponse response = userProfilePort.createRegisteredProfile(user, request.fullName(), roles);
        boolean verificationEmailSent = emailVerificationService.sendVerificationBestEffort(user.getId());
        if (!verificationEmailSent) {
            log.warn("Initial verification email was not sent for {}", user.getEmail());
        }
        return response;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        AuthUser user = authUserRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw AuthenticationException.invalidCredentials();
        }

        Instant now = Instant.now();
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(now)) {
            throw new AccountLockedException(user.getLockUntil());
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            LoginAttemptTracker.Outcome outcome = loginAttemptTracker.recordFailure(
                    user.getId(), LOCKOUT_THRESHOLD, LOCKOUT_DURATION);
            if (outcome.lockUntil() != null) {
                log.warn("Account locked for user {} until {}", user.getEmail(), outcome.lockUntil());
                throw new AccountLockedException(outcome.lockUntil());
            }
            throw AuthenticationException.invalidCredentials();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountSuspendedException();
        }

        loginAttemptTracker.recordSuccess(user.getId());
        user.setLastLoginAt(now);
        authUserRepository.save(user);

        List<Role> roles = userRoleRepository.findByUserId(user.getId())
                .stream().map(UserRole::getRole).toList();

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), roles);
        Instant accessExpiry = tokenProvider.getAccessTokenExpiry();

        RefreshTokenService.CreatedRefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        Instant refreshExpiry = refreshToken.token().getExpiresAt();

        log.info("User logged in: {}", user.getEmail());

        return TokenResponse.of(accessToken, accessExpiry, refreshToken.rawToken(), refreshExpiry,
                userProfilePort.getProfile(user.getId(), user.getEmail(), roles));
    }

    @Transactional
    public TokenOnlyResponse refresh(RefreshRequest request) {
        RefreshToken oldToken = refreshTokenService.findUsableTokenOrRevokeFamilyOnReuse(request.refreshToken());
        if (oldToken == null) {
            throw AuthenticationException.invalidCredentials();
        }

        AuthUser user = oldToken.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountSuspendedException();
        }

        List<Role> roles = userRoleRepository.findByUserId(user.getId())
                .stream().map(UserRole::getRole).toList();

        String newAccessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), roles);
        Instant accessExpiry = tokenProvider.getAccessTokenExpiry();

        RefreshTokenService.CreatedRefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(oldToken);
        Instant refreshExpiry = newRefreshToken.token().getExpiresAt();

        log.info("Token refreshed for user: {}", user.getEmail());

        return TokenOnlyResponse.of(newAccessToken, accessExpiry, newRefreshToken.rawToken(), refreshExpiry);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revokeByToken(request.refreshToken());
        log.info("User logged out");
    }

    private List<Role> resolveRoles(List<String> requestedRoles) {
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            return List.of(Role.CUSTOMER);
        }

        if (requestedRoles.stream().anyMatch(name -> {
            try {
                return Role.valueOf(name.toUpperCase()) == Role.ADMIN;
            } catch (IllegalArgumentException e) {
                return false;
            }
        })) {
            throw new BusinessRuleException("INVALID_ROLE", "Cannot register with ADMIN role");
        }

        List<Role> roles = requestedRoles.stream()
                .map(name -> {
                    try {
                        return Role.valueOf(name.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return roles.isEmpty() ? List.of(Role.CUSTOMER) : roles;
    }
}
