package com.rentflow.integration.notification;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.service.NotificationService;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class NotificationIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    payment_transactions,
                    booking_payments,
                    booking_extras,
                    bookings,
                    availability_calendar,
                    idempotency_keys,
                    listings,
                    vehicles,
                    driver_verifications,
                    notifications,
                    user_profiles,
                    auth_users
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void meEndpointReturnsOnlyCurrentUserNotifications() throws Exception {
        AuthUser userA = saveUserWithProfile("user-a", Role.CUSTOMER);
        AuthUser userB = saveUserWithProfile("user-b", Role.CUSTOMER);
        String tokenA = token(userA, Role.CUSTOMER);

        notificationService.create(
                userA.getId(),
                NotificationType.DRIVER_VERIFICATION_EXPIRED,
                "Driver License Expired",
                "Your driver license has expired. Please submit a new verification.");
        notificationService.create(
                userB.getId(),
                NotificationType.DRIVER_VERIFICATION_EXPIRED,
                "Driver License Expired",
                "Your driver license has expired. Please submit a new verification.");

        mockMvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("DRIVER_VERIFICATION_EXPIRED"));
    }

    @Test
    void meEndpointWithoutAuthReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    private AuthUser saveUserWithProfile(String prefix, Role... roles) {
        return transactionTemplate.execute(tx -> {
            AuthUser user = new AuthUser(prefix + "-" + UUID.randomUUID() + "@example.com", "hash", UserStatus.ACTIVE, true);
            for (Role role : roles) {
                user.addRole(role);
            }
            user = authUserRepository.save(user);
            UserProfile profile = new UserProfile("User " + prefix);
            profile.setUser(user);
            userProfileRepository.save(profile);
            return user;
        });
    }

    private String token(AuthUser user, Role... roles) {
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), List.of(roles));
    }
}
