package com.rentflow.integration.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@Tag("integration")
class AdminListingConcurrentApprovalTest {

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
    private UserRoleRepository userRoleRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private AvailabilityCalendarRepository availabilityRepository;

    private String hostToken;
    private String adminToken;
    private UUID hostUserId;

    @BeforeEach
    void setUp() throws Exception {
        userRoleRepository.deleteAll();
        authUserRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        availabilityRepository.deleteAll();

        JsonNode hostReg = registerUser("host@example.com", "Password@123", "HOST");
        hostUserId = UUID.fromString(hostReg.get("id").asText());
        hostToken = login("host@example.com", "Password@123");

        JsonNode adminReg = registerUser("admin@example.com", "Password@123", "ADMIN");
        adminToken = login("admin@example.com", "Password@123");
    }

    @Test
    void concurrentApproval_sameVehicle_onlyOneSucceeds()
            throws Exception {
        // Create vehicle (status ACTIVE by default)
        String vehicleId = createVehicle();

        // Create listing 1 — submit to PENDING_APPROVAL
        String listing1Id = createListing(vehicleId, "Listing One");
        submitListing(listing1Id);

        // Create listing 2 — submit to PENDING_APPROVAL
        String listing2Id = createListing(vehicleId, "Listing Two");
        submitListing(listing2Id);

        UUID id1 = UUID.fromString(listing1Id);
        UUID id2 = UUID.fromString(listing2Id);

        // Both listings must be PENDING_APPROVAL before running concurrent test
        assertThat(listingRepository.findById(id1).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.PENDING_APPROVAL);
        assertThat(listingRepository.findById(id2).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.PENDING_APPROVAL);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<ApprovalResult>> futures = new ArrayList<>();

        // Thread 1: approves listing 1
        futures.add(executor.submit(() -> {
            startLatch.await(10, TimeUnit.SECONDS);
            return callApprove(id1);
        }));

        // Thread 2: approves listing 2
        futures.add(executor.submit(() -> {
            startLatch.await(10, TimeUnit.SECONDS);
            return callApprove(id2);
        }));

        // Release both threads simultaneously
        startLatch.countDown();

        List<ApprovalResult> results = new ArrayList<>();
        for (Future<ApprovalResult> f : futures) {
            try {
                results.add(f.get(10, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                results.add(new ApprovalResult(500, null, true, e));
            }
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(terminated).isTrue();

        // --- Assertions ---
        long okCount = results.stream().filter(r -> r.httpStatus == 200).count();
        long conflictCount = results.stream().filter(r -> r.httpStatus == 409).count();
        long serverErrorCount = results.stream().filter(r -> r.httpStatus == 500).count();

        assertThat(okCount)
                .describedAs("Exactly one approval must succeed (HTTP 200)")
                .isEqualTo(1);
        assertThat(conflictCount)
                .describedAs("Exactly one approval must fail with HTTP 409")
                .isEqualTo(1);
        assertThat(serverErrorCount)
                .describedAs("No HTTP 500 responses")
                .isZero();

        // The conflict result must carry the correct code
        results.stream()
                .filter(r -> r.httpStatus == 409)
                .forEach(r -> assertThat(r.code)
                        .isEqualTo("ONE_ACTIVE_LISTING_PER_VEHICLE"));

        // DB must contain exactly one ACTIVE listing for this vehicle
        long activeCount = listingRepository.countByVehicleIdAndStatusIn(
                UUID.fromString(vehicleId), Set.of(ListingStatus.ACTIVE));
        assertThat(activeCount)
                .describedAs("Exactly 1 ACTIVE listing for this vehicle in DB")
                .isEqualTo(1);
    }

    private ApprovalResult callApprove(UUID listingId) {
        try {
            MvcResult result = mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/approve")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            int status = result.getResponse().getStatus();
            String body = result.getResponse().getContentAsString();
            JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            String code = json.has("code") ? json.get("code").asText() : null;
            return new ApprovalResult(status, code, false, null);
        } catch (DataIntegrityViolationException ex) {
            return new ApprovalResult(409, "ONE_ACTIVE_LISTING_PER_VEHICLE", false, null);
        } catch (Exception ex) {
            return new ApprovalResult(500, null, true, ex);
        }
    }

    private record ApprovalResult(int httpStatus, String code, boolean exception, Throwable thrown) {}

    // Helpers — identical to ListingLifecycleIntegrationTest

    private JsonNode registerUser(String email, String password, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s",
                      "fullName": "Test User"
                    }
                    """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn();

        String userId = parseJson(result).get("id").asText();
        var user = authUserRepository.findById(UUID.fromString(userId)).orElseThrow();
        Role requestedRole = Role.valueOf(role);
        if (requestedRole != Role.CUSTOMER) {
            userRoleRepository.save(new UserRole(user, requestedRole));
        }

        return parseJson(result);
    }

    private String login(String email, String password) throws Exception {
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
        return parseJson(result).get("accessToken").asText();
    }

    private String createVehicle() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/host/vehicles")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "category": "SEDAN",
                              "make": "Toyota",
                              "model": "Camry",
                              "year": 2020,
                              "plateNumber": "ABC-123",
                              "transmission": "AUTO",
                              "fuelType": "PETROL",
                              "seats": 5,
                              "city": "Hanoi"
                            }
                            """))
                .andExpect(status().isCreated())
                .andReturn();
        return parseJson(result).get("id").asText();
    }

    private String createListing(String vehicleId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/host/listings")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "vehicleId": "%s",
                              "title": "%s",
                              "city": "Hanoi",
                              "basePricePerDay": 500000
                            }
                            """.formatted(vehicleId, title)))
                .andExpect(status().isCreated())
                .andReturn();
        return parseJson(result).get("id").asText();
    }

    private void submitListing(String listingId) throws Exception {
        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    private JsonNode parseJson(MvcResult result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString());
    }
}
