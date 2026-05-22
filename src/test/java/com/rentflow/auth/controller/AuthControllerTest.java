package com.rentflow.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.dto.LoginRequest;
import com.rentflow.auth.dto.TokenResponse;
import com.rentflow.auth.service.AuthService;
import com.rentflow.common.exception.AuthenticationException;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.GlobalExceptionHandler;
import com.rentflow.common.exception.RateLimitExceededException;
import com.rentflow.common.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthService authService;
    private RateLimitService rateLimitService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        rateLimitService = mock(RateLimitService.class);
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
                        authService,
                        rateLimitService,
                        mock(com.rentflow.auth.service.PasswordService.class),
                        mock(com.rentflow.auth.service.EmailVerificationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler(new CorrelationIdHelper()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void loginFailureRecordsRateLimitAttempt() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "bad-password");
        when(authService.login(request)).thenThrow(AuthenticationException.invalidCredentials());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("203.0.113.10");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        verify(rateLimitService).checkLoginAllowed("user@example.com", "203.0.113.10");
        verify(rateLimitService).recordLoginFailure("user@example.com", "203.0.113.10");
    }

    @Test
    void loginSuccessClearsFailedAttempts() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Password@123");
        when(authService.login(request)).thenReturn(TokenResponse.of(
                "access-token",
                Instant.parse("2026-06-01T00:15:00Z"),
                "refresh-token",
                Instant.parse("2026-06-08T00:00:00Z"),
                null));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "198.51.100.7, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));

        verify(rateLimitService).checkLoginAllowed("user@example.com", "198.51.100.7");
        verify(rateLimitService).clearLoginFailures("user@example.com", "198.51.100.7");
    }

    @Test
    void loginRateLimitedReturns429() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Password@123");
        doThrow(new RateLimitExceededException(Duration.ofSeconds(60)))
                .when(rateLimitService).checkLoginAllowed("user@example.com", "203.0.113.10");

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("203.0.113.10");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
