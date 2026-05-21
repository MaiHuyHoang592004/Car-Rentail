package com.rentflow.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.auth.dto.LoginRequest;
import com.rentflow.auth.dto.RefreshRequest;
import com.rentflow.auth.dto.RegisterRequest;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.RefreshToken;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.RefreshTokenRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.auth.service.RefreshTokenService;
import com.rentflow.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@Tag("integration")
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rentflow")
            .withUsername("rentflow")
            .withPassword("rentflow");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        userProfileRepository.deleteAllInBatch();
        authUserRepository.deleteAll();
    }

    // ─── Register ─────────────────────────────────────────────────────────────

    @Test
    void register_createsUserWithCustomerRoleAndProfile() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "Password@123",
                                  "fullName": "Alice Smith"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alice Smith"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.driverVerificationStatus").value("NOT_SUBMITTED"));

        assertThat(authUserRepository.existsByEmail("alice@example.com")).isTrue();
        assertThat(userProfileRepository.findByUserId(
                authUserRepository.findByEmail("alice@example.com").get().getId()
        )).isPresent();
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dup@example.com",
                                  "password": "Password@123",
                                  "fullName": "First"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dup@example.com",
                                  "password": "Password@456",
                                  "fullName": "Second"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_EXISTS"));
    }

    @Test
    void register_adminRoleRequest_doesNotAssignAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-admin@example.com",
                                  "password": "Password@123",
                                  "fullName": "Not Admin",
                                  "roles": ["ADMIN"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    }

    // ─── Login ─────────────────────────────────────────────────────────────────

    @Test
    void login_success_returnsTokensAndUser() throws Exception {
        registerUser("bob@example.com", "Password@123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bob@example.com",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.accessTokenExpiresAt").exists())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").exists())
                .andExpect(jsonPath("$.user.id").exists())
                .andExpect(jsonPath("$.user.email").value("bob@example.com"))
                .andReturn();

        String rawRefreshToken = parseJson(result).get("refreshToken").asText();
        RefreshToken storedToken = refreshTokenRepository.findAll().get(0);
        assertThat(storedToken.getTokenHash()).isNotEqualTo(rawRefreshToken);
        assertThat(storedToken.getTokenHash()).isEqualTo(refreshTokenService.sha256(rawRefreshToken));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        registerUser("carl@example.com", "CorrectPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "carl@example.com",
                                  "password": "WrongPassword"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void login_nonexistentEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "nobody@example.com",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void login_suspendedUser_returns401() throws Exception {
        AuthUser user = new AuthUser("suspended-login@example.com", "{noop}Password@123", UserStatus.SUSPENDED, false);
        user = authUserRepository.save(user);
        user.getRoles().add(new UserRole(user, Role.CUSTOMER));
        authUserRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "suspended-login@example.com",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    // ─── Refresh ──────────────────────────────────────────────────────────────

    @Test
    void refresh_rotateToken_invalidatesOldToken() throws Exception {
        registerUser("dana@example.com", "Password@123");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dana@example.com",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginNode = parseJson(loginResult);
        String oldRefreshToken = loginNode.get("refreshToken").asText();

        // Refresh using the old token
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode refreshNode = parseJson(refreshResult);
        String newRefreshToken = refreshNode.get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

        // Old token must not work again
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isUnauthorized());

        // Reusing a rotated token is treated as theft and revokes the active replacement too.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(newRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @Test
    void logout_revokesRefreshToken() throws Exception {
        registerUser("eve@example.com", "Password@123");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "eve@example.com",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = parseJson(loginResult).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        // After logout, refresh token should not work
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_repeatedLogout_isHarmless() throws Exception {
        registerUser("frank@example.com", "Password@123");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "frank@example.com",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = parseJson(loginResult).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void registerUser(String email, String password) throws Exception {
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
    }

    private JsonNode parseJson(MvcResult result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString());
    }
}
