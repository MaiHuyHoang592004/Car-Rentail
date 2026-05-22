package com.rentflow.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.EmailVerificationToken;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.EmailVerificationTokenRepository;
import com.rentflow.auth.service.PasswordService;
import com.rentflow.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class EmailVerificationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @BeforeEach
    void clean() {
        emailVerificationTokenRepository.deleteAll();
        authUserRepository.deleteAll();
    }

    @Test
    @DisplayName("resend-verification creates a token for the current user")
    void resendVerification_createsToken() throws Exception {
        String token = registerAndLogin("verify-me@example.com", "Password@123");

        mockMvc.perform(post("/api/v1/users/me/resend-verification")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        List<EmailVerificationToken> tokens = emailVerificationTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("verify-email with valid token marks user as verified")
    void verifyEmail_validToken_setsEmailVerified() throws Exception {
        registerAndLogin("verify-ok@example.com", "Password@123");
        AuthUser user = authUserRepository.findByEmail("verify-ok@example.com").orElseThrow();
        assertThat(user.getEmailVerified()).isFalse();

        String raw = "verification-raw-token-abc123";
        emailVerificationTokenRepository.save(new EmailVerificationToken(
                user.getId(),
                PasswordService.sha256(raw),
                Instant.now().plusSeconds(3600)));

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "token": "%s" }
                                """.formatted(raw)))
                .andExpect(status().isNoContent());

        AuthUser refreshed = authUserRepository.findByEmail("verify-ok@example.com").orElseThrow();
        assertThat(refreshed.getEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("verify-email with invalid token returns 409")
    void verifyEmail_invalidToken_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "token": "no-such-token" }
                                """))
                .andExpect(status().isConflict());
    }

    private String registerAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "fullName": "Test User"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isCreated());
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(res.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
