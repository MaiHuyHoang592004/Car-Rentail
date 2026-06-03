package com.rentflow.integration.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.RefreshToken;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.RefreshTokenRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
class UserIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        userProfileRepository.deleteAllInBatch();
        authUserRepository.deleteAll();
    }

    // ─── GET /users/me ────────────────────────────────────────────────────────

    @Test
    void getMe_returnsProfileWithoutSensitiveFields() throws Exception {
        AuthUser user = createUser("me@example.com", "Password@123", Role.CUSTOMER);
        String token = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), List.of(Role.CUSTOMER));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.fullName").value("Me User"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.driverVerificationStatus").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist());
    }

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Correlation-Id", "get-me-unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.correlationId").value("get-me-unauthenticated"));
    }

    @Test
    void getMe_suspendedUserWithValidJwt_returns401() throws Exception {
        AuthUser user = createUser("suspended-jwt@example.com", "Password@123", Role.CUSTOMER);
        String token = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), List.of(Role.CUSTOMER));

        user.setStatus(UserStatus.SUSPENDED);
        authUserRepository.save(user);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_SUSPENDED"));
    }

    // ─── PATCH /users/me ──────────────────────────────────────────────────────

    @Test
    void patchMe_updatesAllowedFields() throws Exception {
        AuthUser user = createUser("patch@example.com", "Password@123", Role.CUSTOMER);
        String token = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), List.of(Role.CUSTOMER));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Patched Name",
                                  "phone": "0909000000",
                                  "addressLine": "New Address"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Patched Name"))
                .andExpect(jsonPath("$.phone").value("0909000000"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.addressLine").value("New Address"));
    }

    @Test
    void patchMe_rejectsInvalidPhone() throws Exception {
        AuthUser user = createUser("invalid-phone@example.com", "Password@123", Role.CUSTOMER);
        String token = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), List.of(Role.CUSTOMER));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "0909-call-me"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field=='phone')]").exists());
    }

    @Test
    void patchMe_ignoresProtectedFields() throws Exception {
        AuthUser user = createUser("protected-fields@example.com", "Password@123", Role.CUSTOMER);
        String token = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), List.of(Role.CUSTOMER));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Allowed Name",
                                  "email": "changed@example.com",
                                  "roles": ["ADMIN"],
                                  "status": "SUSPENDED",
                                  "driverVerificationStatus": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Allowed Name"))
                .andExpect(jsonPath("$.email").value("protected-fields@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.driverVerificationStatus").value("NOT_SUBMITTED"));
    }

    @Test
    void changePassword_withCorrectCurrentPasswordRevokesOldRefreshTokenAndAllowsNewLogin() throws Exception {
        registerUser("password-change@example.com", "Password@123");
        JsonNode login = loginUser("password-change@example.com", "Password@123");
        String accessToken = login.get("accessToken").asText();
        String oldRefreshToken = login.get("refreshToken").asText();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "Password@123",
                                  "newPassword": "NewPassword@123"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "password-change@example.com",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "password-change@example.com",
                                  "password": "NewPassword@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(oldRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_withWrongCurrentPasswordReturns401() throws Exception {
        registerUser("password-wrong-current@example.com", "Password@123");
        JsonNode login = loginUser("password-wrong-current@example.com", "Password@123");
        String accessToken = login.get("accessToken").asText();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "WrongPassword@123",
                                  "newPassword": "NewPassword@123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    // ─── Admin: GET /admin/users ───────────────────────────────────────────────

    @Test
    void adminListUsers_requiresAdminRole() throws Exception {
        AuthUser customer = createUser("customer@example.com", "Password@123", Role.CUSTOMER);
        String customerToken = tokenProvider.generateAccessToken(customer.getId(), customer.getEmail(), List.of(Role.CUSTOMER));

        mockMvc.perform(get("/api/v1/admin/users")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminListUsers_withAdminRole_succeeds() throws Exception {
        AuthUser admin = createUser("admin@example.com", "Password@123", Role.ADMIN);
        AuthUser customer = createUser("cust@example.com", "Password@123", Role.CUSTOMER);
        String adminToken = tokenProvider.generateAccessToken(admin.getId(), admin.getEmail(), List.of(Role.ADMIN));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void adminListUsers_filtersByStatus() throws Exception {
        AuthUser admin = createUser("admin2@example.com", "Password@123", Role.ADMIN);
        createUser("active@example.com", "Password@123", Role.CUSTOMER, UserStatus.ACTIVE);
        AuthUser suspended = createUser("suspended@example.com", "Password@123", Role.CUSTOMER, UserStatus.SUSPENDED);

        String adminToken = tokenProvider.generateAccessToken(admin.getId(), admin.getEmail(), List.of(Role.ADMIN));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminListUsers_filtersByRole() throws Exception {
        AuthUser admin = createUser("admin3@example.com", "Password@123", Role.ADMIN);
        createUser("cust2@example.com", "Password@123", Role.CUSTOMER);
        createUser("host2@example.com", "Password@123", Role.HOST);

        String adminToken = tokenProvider.generateAccessToken(admin.getId(), admin.getEmail(), List.of(Role.ADMIN));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("role", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminListUsers_invalidStatusReturns400() throws Exception {
        AuthUser admin = createUser("admin4@example.com", "Password@123", Role.ADMIN);
        String adminToken = tokenProvider.generateAccessToken(admin.getId(), admin.getEmail(), List.of(Role.ADMIN));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void adminListUsers_invalidRoleReturns400() throws Exception {
        AuthUser admin = createUser("admin5@example.com", "Password@123", Role.ADMIN);
        String adminToken = tokenProvider.generateAccessToken(admin.getId(), admin.getEmail(), List.of(Role.ADMIN));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("role", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AuthUser createUser(String email, String password, Role role) {
        return createUser(email, password, role, UserStatus.ACTIVE);
    }

    private void registerUser(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "fullName": "Registered User"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isCreated());
    }

    private JsonNode loginUser(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString());
    }

    private AuthUser createUser(String email, String password, Role role, UserStatus status) {
        return transactionTemplate.execute(tx -> {
            AuthUser user = new AuthUser(email, "{noop}" + password, status, false);
            user.addRole(role);
            user = authUserRepository.save(user);

            UserProfile profile = new UserProfile("Me User");
            profile.setUser(user);
            userProfileRepository.save(profile);

            return user;
        });
    }
}
