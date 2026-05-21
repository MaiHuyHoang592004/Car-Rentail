package com.rentflow.auth.service;

import com.rentflow.auth.dto.*;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.RefreshToken;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.common.exception.AuthenticationException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.user.dto.RegisterResponse;
import com.rentflow.user.dto.UserProfileResponse;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.UserProfileRepository;
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

    private final AuthUserRepository authUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

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

        UserProfile profile = new UserProfile(request.fullName());
        profile.setUser(user);
        userProfileRepository.save(profile);

        log.info("User registered: {} with roles {}", user.getEmail(), roles);

        return RegisterResponse.from(user, profile, roles);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        AuthUser user = authUserRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw AuthenticationException.invalidCredentials();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthenticationException.invalidCredentials();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw AuthenticationException.invalidCredentials();
        }

        user.setLastLoginAt(Instant.now());
        authUserRepository.save(user);

        List<Role> roles = userRoleRepository.findByUserId(user.getId())
                .stream().map(UserRole::getRole).toList();

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), roles);
        Instant accessExpiry = tokenProvider.getAccessTokenExpiry();

        RefreshTokenService.CreatedRefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        Instant refreshExpiry = refreshToken.token().getExpiresAt();

        UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElseThrow();
        UserProfileResponse userResponse = UserProfileResponse.from(user.getId(), user.getEmail(), roles, profile);

        log.info("User logged in: {}", user.getEmail());

        return TokenResponse.of(accessToken, accessExpiry, refreshToken.rawToken(), refreshExpiry, userResponse);
    }

    @Transactional
    public TokenOnlyResponse refresh(RefreshRequest request) {
        RefreshToken oldToken = refreshTokenService.findUsableTokenOrRevokeFamilyOnReuse(request.refreshToken());
        if (oldToken == null) {
            throw AuthenticationException.invalidCredentials();
        }

        AuthUser user = oldToken.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthenticationException.invalidCredentials();
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

        List<Role> roles = requestedRoles.stream()
                .map(name -> {
                    try {
                        return Role.valueOf(name.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(r -> r != null && r != Role.ADMIN)
                .toList();

        return roles.isEmpty() ? List.of(Role.CUSTOMER) : roles;
    }
}
