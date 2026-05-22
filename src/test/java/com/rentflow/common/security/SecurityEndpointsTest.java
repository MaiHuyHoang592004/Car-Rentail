package com.rentflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.dto.LoginRequest;
import com.rentflow.auth.dto.RegisterRequest;
import com.rentflow.auth.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:webtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "jwt.secret=test-jwt-secret-key-for-unit-tests-only-minimum-60-bytes-required-for-hs512",
        "jwt.access-token-expiry=PT15M",
        "jwt.refresh-token-expiry=P7D"
})
class SecurityEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorHealthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Correlation-Id", "security-test-cid"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.correlationId").value("security-test-cid"));
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Correlation-Id", "invalid-token-cid")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.correlationId").value("invalid-token-cid"));
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401TokenExpired() throws Exception {
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(
                "test-jwt-secret-key-for-unit-tests-only-minimum-60-bytes-required-for-hs512",
                Duration.ofSeconds(-1),
                Duration.ofDays(7));
        String expiredToken = expiredTokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "expired@example.com",
                java.util.List.of(Role.CUSTOMER));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").value("Access token has expired"));
    }

    @Test
    void hostEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/host/vehicles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void customerToken_onRoleProtectedEndpoints_returns403() throws Exception {
        String customerToken = tokenProvider.generateAccessToken(
                UUID.randomUUID(),
                "customer@example.com",
                java.util.List.of(Role.CUSTOMER));

        mockMvc.perform(get("/api/v1/host/vehicles")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void jwtTokenProvider_generatesValidTokens() throws Exception {
        // Test that the JwtTokenProvider can generate and validate tokens
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        
        String token = tokenProvider.generateAccessToken(userId, email, java.util.List.of(Role.CUSTOMER));
        
        // Token should be a non-empty string
        assert token != null && !token.isEmpty();
        
        // Token should contain three parts separated by dots
        assert token.split("\\.").length == 3;
    }

    @Test
    void register_isPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new@example.com",
                                  "password": "Password@123",
                                  "fullName": "New User"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "nonexistent@example.com",
                                  "password": "WrongPassword"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }
}
