package com.rentflow.integration.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@Tag("integration")
@DisplayName("Vehicle Suspend Propagation")
class VehicleSuspendPropagationIntegrationTest {

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
    @DisplayName("vehicle ACTIVE -> SUSPENDED auto-suspends ACTIVE listings")
    void vehicleToSuspended_autoSuspendsActiveListings() throws Exception {
        // Create vehicle (ACTIVE by default)
        String vehicleId = createVehicle();

        // Create and submit listing 1
        String listing1Id = createListing(vehicleId, "Listing One");
        submitListing(listing1Id);
        approveListing(listing1Id);

        // Create and submit listing 2
        String listing2Id = createListing(vehicleId, "Listing Two");
        submitListing(listing2Id);
        approveListing(listing2Id);

        // Both listings must be ACTIVE
        assertThat(listingRepository.findById(UUID.fromString(listing1Id)).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.ACTIVE);
        assertThat(listingRepository.findById(UUID.fromString(listing2Id)).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.ACTIVE);

        // Suspend the vehicle
        mockMvc.perform(patch("/api/v1/host/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "status": "SUSPENDED" }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // Both listings must now be SUSPENDED
        assertThat(listingRepository.findById(UUID.fromString(listing1Id)).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.SUSPENDED);
        assertThat(listingRepository.findById(UUID.fromString(listing2Id)).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.SUSPENDED);
    }

    @Test
    @DisplayName("vehicle ACTIVE -> MAINTENANCE auto-suspends ACTIVE listings")
    void vehicleToMaintenance_autoSuspendsActiveListings() throws Exception {
        String vehicleId = createVehicle();

        String listingId = createListing(vehicleId, "Listing For Maintenance");
        submitListing(listingId);
        approveListing(listingId);

        assertThat(listingRepository.findById(UUID.fromString(listingId)).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/host/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "status": "MAINTENANCE" }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));

        assertThat(listingRepository.findById(UUID.fromString(listingId)).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.SUSPENDED);
    }

    @Test
    @DisplayName("vehicle SUSPENDED -> ACTIVE does not resurrect SUSPENDED listings")
    void vehicleToActive_doesNotAffectSuspendedListings() throws Exception {
        String vehicleId = createVehicle();

        // Listing 1: ACTIVE
        String listing1Id = createListing(vehicleId, "Listing Active");
        submitListing(listing1Id);
        approveListing(listing1Id);

        // Listing 2: will be suspended
        String listing2Id = createListing(vehicleId, "Listing Suspended");
        submitListing(listing2Id);
        approveListing(listing2Id);

        // Suspend vehicle -> both listings suspend
        mockMvc.perform(patch("/api/v1/host/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "status": "SUSPENDED" }
                            """))
                .andExpect(status().isOk());

        assertThat(listingRepository.findById(UUID.fromString(listing1Id)).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.SUSPENDED);
        assertThat(listingRepository.findById(UUID.fromString(listing2Id)).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.SUSPENDED);

        // Bring vehicle back to ACTIVE
        mockMvc.perform(patch("/api/v1/host/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "status": "ACTIVE" }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Both listings must remain SUSPENDED (not resurrected)
        assertThat(listingRepository.findById(UUID.fromString(listing1Id)).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.SUSPENDED);
        assertThat(listingRepository.findById(UUID.fromString(listing2Id)).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.SUSPENDED);
    }

    // Helpers

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
        user.getRoles().add(new UserRole(user, Role.valueOf(role)));
        authUserRepository.save(user);

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
                              "seats": 5
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
                .andExpect(status().isOk());
    }

    private void approveListing(String listingId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private JsonNode parseJson(MvcResult result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString());
    }
}
