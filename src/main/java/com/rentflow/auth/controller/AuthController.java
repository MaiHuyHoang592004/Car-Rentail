package com.rentflow.auth.controller;

import com.rentflow.auth.dto.*;
import com.rentflow.auth.service.AuthService;
import com.rentflow.auth.service.EmailVerificationService;
import com.rentflow.auth.service.PasswordService;
import com.rentflow.common.exception.AuthenticationException;
import com.rentflow.common.ratelimit.RateLimitService;
import com.rentflow.common.security.ClientIpResolver;
import com.rentflow.common.security.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final SecurityContext securityContext;
    private final PasswordService passwordService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = clientIpResolver.resolve(servletRequest);
        rateLimitService.consumeRegister(clientIp);
        var response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = clientIpResolver.resolve(servletRequest);
        rateLimitService.checkLoginAllowed(request.email(), clientIp);
        try {
            var response = authService.login(request);
            rateLimitService.clearLoginFailures(request.email(), clientIp);
            return ResponseEntity.ok(response);
        } catch (AuthenticationException ex) {
            rateLimitService.recordLoginFailure(request.email(), clientIp);
            throw ex;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenOnlyResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        var response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll() {
        authService.logoutAll(securityContext.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.noContent().build();
    }
}
