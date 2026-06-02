package com.rentflow.integration.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.file.entity.FileMetadata;
import com.rentflow.file.entity.FilePurpose;
import com.rentflow.file.entity.FileStatus;
import com.rentflow.file.entity.FileVisibility;
import com.rentflow.file.repository.FileMetadataRepository;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.user.entity.DriverVerification;
import com.rentflow.user.entity.DriverVerificationStatus;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.DriverVerificationRepository;
import com.rentflow.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class DriverVerificationIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private DriverVerificationRepository driverVerificationRepository;
    @Autowired private FileMetadataRepository fileMetadataRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ObjectMapper objectMapper;
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
                    files,
                    user_profiles,
                    auth_users
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void submitDriverLicenseSuccessCreatesPendingAndSyncsProfile() throws Exception {
        AuthUser customer = saveUserWithProfile("customer-submit", Role.CUSTOMER);
        String token = token(customer, Role.CUSTOMER);
        UUID documentFileId = saveDriverLicenseFile(customer.getId());

        var result = mockMvc.perform(post("/api/v1/users/me/driver-license")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(documentFileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID verificationId = UUID.fromString(json.get("id").asText());
        assertThat(driverVerificationRepository.findById(verificationId)).isPresent();
        assertThat(userProfileRepository.findByUserId(customer.getId()).orElseThrow().getDriverVerificationStatus())
                .isEqualTo(UserProfile.DriverVerificationStatus.PENDING);
    }

    @Test
    void submitDriverLicenseAllowsDemoUuidWithoutStoredFile() throws Exception {
        AuthUser customer = saveUserWithProfile("customer-demo-uuid", Role.CUSTOMER);
        String token = token(customer, Role.CUSTOMER);
        UUID documentFileId = UUID.fromString("7f9c1b2e-3a44-4d0e-9d12-6b8e5f0c1234");

        mockMvc.perform(post("/api/v1/users/me/driver-license")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(documentFileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.documentFileId").value(documentFileId.toString()))
                .andExpect(jsonPath("$.documentPreviewUrl").value(nullValue()));

        DriverVerification verification = driverVerificationRepository.findAll().get(0);
        assertThat(verification.getDocumentFileId()).isEqualTo(documentFileId);
        assertThat(userProfileRepository.findByUserId(customer.getId()).orElseThrow().getDriverVerificationStatus())
                .isEqualTo(UserProfile.DriverVerificationStatus.PENDING);
    }

    @Test
    void duplicateSubmitReturnsAlreadySubmitted() throws Exception {
        AuthUser customer = saveUserWithProfile("customer-duplicate", Role.CUSTOMER);
        String token = token(customer, Role.CUSTOMER);
        UUID documentFileId = saveDriverLicenseFile(customer.getId());

        mockMvc.perform(post("/api/v1/users/me/driver-license")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(documentFileId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/me/driver-license")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(documentFileId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
    }

    @Test
    void resubmitAfterRejectedIsAllowed() throws Exception {
        AuthUser customer = saveUserWithProfile("customer-rejected", Role.CUSTOMER);
        String token = token(customer, Role.CUSTOMER);
        UUID documentFileId = saveDriverLicenseFile(customer.getId());
        saveHistoricalVerification(customer.getId(), DriverVerificationStatus.REJECTED);

        mockMvc.perform(post("/api/v1/users/me/driver-license")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(documentFileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void resubmitAfterExpiredIsAllowed() throws Exception {
        AuthUser customer = saveUserWithProfile("customer-expired", Role.CUSTOMER);
        String token = token(customer, Role.CUSTOMER);
        UUID documentFileId = saveDriverLicenseFile(customer.getId());
        saveHistoricalVerification(customer.getId(), DriverVerificationStatus.EXPIRED);

        mockMvc.perform(post("/api/v1/users/me/driver-license")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(documentFileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void adminApproveUpdatesVerificationAndProfile() throws Exception {
        AuthUser customer = saveUserWithProfile("customer-approve", Role.CUSTOMER);
        AuthUser admin = saveUserWithProfile("admin-approve", Role.ADMIN);
        String customerToken = token(customer, Role.CUSTOMER);
        String adminToken = token(admin, Role.ADMIN);
        UUID documentFileId = saveDriverLicenseFile(customer.getId());

        var submitResult = mockMvc.perform(post("/api/v1/users/me/driver-license")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(documentFileId)))
                .andReturn();
        UUID verificationId = UUID.fromString(
                objectMapper.readTree(submitResult.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/api/v1/admin/driver-verifications/{id}/approve", verificationId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"verified by admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(userProfileRepository.findByUserId(customer.getId()).orElseThrow().getDriverVerificationStatus())
                .isEqualTo(UserProfile.DriverVerificationStatus.APPROVED);
    }

    @Test
    void adminRejectUpdatesVerificationAndProfile() throws Exception {
        AuthUser customer = saveUserWithProfile("customer-reject", Role.CUSTOMER);
        AuthUser admin = saveUserWithProfile("admin-reject", Role.ADMIN);
        String customerToken = token(customer, Role.CUSTOMER);
        String adminToken = token(admin, Role.ADMIN);
        UUID documentFileId = saveDriverLicenseFile(customer.getId());

        var submitResult = mockMvc.perform(post("/api/v1/users/me/driver-license")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(documentFileId)))
                .andReturn();
        UUID verificationId = UUID.fromString(
                objectMapper.readTree(submitResult.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/api/v1/admin/driver-verifications/{id}/reject", verificationId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"mismatch"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(userProfileRepository.findByUserId(customer.getId()).orElseThrow().getDriverVerificationStatus())
                .isEqualTo(UserProfile.DriverVerificationStatus.REJECTED);
    }

    @Test
    void adminCanListByStatusAndNonAdminCannotReview() throws Exception {
        AuthUser customer = saveUserWithProfile("customer-list", Role.CUSTOMER);
        AuthUser admin = saveUserWithProfile("admin-list", Role.ADMIN);
        String customerToken = token(customer, Role.CUSTOMER);
        String adminToken = token(admin, Role.ADMIN);
        UUID documentFileId = saveDriverLicenseFile(customer.getId());

        mockMvc.perform(post("/api/v1/users/me/driver-license")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(documentFileId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/driver-verifications")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        mockMvc.perform(get("/api/v1/admin/driver-verifications")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void hostCannotSubmitDriverLicense() throws Exception {
        AuthUser host = saveUserWithProfile("host-submit", Role.HOST);
        String hostToken = token(host, Role.HOST);
        UUID documentFileId = saveDriverLicenseFile(host.getId());

        mockMvc.perform(post("/api/v1/users/me/driver-license")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(documentFileId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private AuthUser saveUserWithProfile(String prefix, Role role) {
        return transactionTemplate.execute(tx -> {
            AuthUser user = new AuthUser(prefix + "-" + UUID.randomUUID() + "@example.com", "hash", UserStatus.ACTIVE, true);
            user.addRole(role);
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

    private void saveHistoricalVerification(UUID customerId, DriverVerificationStatus status) {
        DriverVerification verification = new DriverVerification();
        verification.setCustomerId(customerId);
        verification.setStatus(status);
        verification.setLicenseNumberEncrypted("enc");
        verification.setLicenseNumberHash("hash");
        verification.setLicenseExpiryDate(java.time.LocalDate.of(2025, 1, 1));
        verification.setDocumentFileId(UUID.randomUUID());
        driverVerificationRepository.save(verification);
    }

    private UUID saveDriverLicenseFile(UUID ownerUserId) {
        FileMetadata file = new FileMetadata();
        file.setOwnerUserId(ownerUserId);
        file.setPurpose(FilePurpose.DRIVER_LICENSE);
        file.setBucket("test-bucket");
        file.setObjectKey("driver-licenses/" + ownerUserId + ".jpg");
        file.setContentType("image/jpeg");
        file.setSizeBytes(1024L);
        file.setVisibility(FileVisibility.PRIVATE);
        file.setStatus(FileStatus.ACTIVE);
        return fileMetadataRepository.save(file).getId();
    }

    private String submitBody(UUID documentFileId) {
        return """
                {
                  "licenseNumber":"A123456789",
                  "licenseExpiryDate":"2028-12-31",
                  "documentFileId":"%s"
                }
                """.formatted(documentFileId);
    }
}
