package com.rentflow.integration.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.outbox.repository.OutboxEventRepository;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
class ListingLifecycleIntegrationTest extends BaseIntegrationTest {

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

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private String hostToken;
    private String adminToken;
    private UUID hostUserId;
    private UUID adminUserId;

    @BeforeEach
    void setUp() throws Exception {
        userRoleRepository.deleteAll();
        authUserRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        availabilityRepository.deleteAll();
        outboxEventRepository.deleteAll();

        JsonNode hostReg = registerUser("host@example.com", "Password@123", "HOST");
        hostUserId = UUID.fromString(hostReg.get("id").asText());
        hostToken = login("host@example.com", "Password@123");

        JsonNode adminReg = registerUser("admin@example.com", "Password@123", "ADMIN");
        adminUserId = UUID.fromString(adminReg.get("id").asText());
        adminToken = login("admin@example.com", "Password@123");
    }

    @Test
    void createListing_defaultsToDraft() throws Exception {
        String vehicleId = createVehicle();

        mockMvc.perform(post("/api/v1/host/listings")
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "vehicleId": "%s",
                      "title": "Cozy Camry",
                      "description": "Great car for city driving",
                      "city": "Hanoi",
                      "basePricePerDay": 500000
                    }
                    """.formatted(vehicleId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.title").value("Cozy Camry"));
    }

    @Test
    void submitListing_transitionsToPendingApproval() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                .header("Authorization", "Bearer " + hostToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void submitListing_doubleSubmit_returns409() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                .header("Authorization", "Bearer " + hostToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                .header("Authorization", "Bearer " + hostToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
    }

    @Test
    void submitNonDraftListing_returns409() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                .header("Authorization", "Bearer " + hostToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                .header("Authorization", "Bearer " + hostToken))
            .andExpect(status().isConflict());
    }

    @Test
    void adminApprove_creates365AvailabilityRows() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                .header("Authorization", "Bearer " + hostToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/approve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        long count = availabilityRepository.countByListingId(UUID.fromString(listingId));
        assertThat(count).isEqualTo(365);
        assertThat(outboxEventRepository.findAll())
                .anySatisfy(event -> {
                    assertThat(event.getAggregateType()).isEqualTo("LISTING");
                    assertThat(event.getAggregateId()).isEqualTo(UUID.fromString(listingId));
                    assertThat(event.getEventType()).isEqualTo("LISTING_APPROVED");
                });

        var rows = availabilityRepository.findByListingIdAndAvailableDateBetweenOrderByAvailableDateAsc(
            UUID.fromString(listingId), LocalDate.now(), LocalDate.now().plusDays(10));
        assertThat(rows).hasSize(11);
        assertThat(rows).allMatch(r -> r.getStatus() == com.rentflow.availability.entity.AvailabilityStatus.FREE);
    }

    @Test
    void adminApprove_withNonActiveVehicle_returns409() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                .header("Authorization", "Bearer " + hostToken))
            .andExpect(status().isOk());

        var vehicle = vehicleRepository.findById(UUID.fromString(vehicleId)).orElseThrow();
        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        vehicleRepository.save(vehicle);

        mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/approve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VEHICLE_NOT_ACTIVE"));
    }

    @Test
    void hostCannotAccessAdminEndpoints() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/approve")
                .header("Authorization", "Bearer " + hostToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminReject_transitionsToDraft() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                .header("Authorization", "Bearer " + hostToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/reject")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "reason": "Incomplete description" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"));

        assertThat(outboxEventRepository.findAll())
                .anySatisfy(event -> {
                    assertThat(event.getAggregateType()).isEqualTo("LISTING");
                    assertThat(event.getAggregateId()).isEqualTo(UUID.fromString(listingId));
                    assertThat(event.getEventType()).isEqualTo("LISTING_REJECTED");
                    assertThat(event.getPayload()).contains("Incomplete description");
                });
    }

    @Test
    void hostCannotAccessAnotherHostsListing() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        JsonNode otherReg = registerUser("otherhost@example.com", "Password@123", "HOST");
        String otherToken = login("otherhost@example.com", "Password@123");

        mockMvc.perform(get("/api/v1/host/listings/" + listingId)
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void hostListingDetail_returnsSuspensionMetadataForAdminSuspendedListing() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/suspend")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "  Missing registration proof  ",
                                  "source": "ADMIN_REVIEW",
                                  "suspensionUntil": "2026-06-15T10:30:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspensionReason").value("Missing registration proof"))
                .andExpect(jsonPath("$.suspensionSource").value("ADMIN_REVIEW"))
                .andExpect(jsonPath("$.suspensionUntil").value("2026-06-15T10:30:00Z"));

        mockMvc.perform(get("/api/v1/host/listings/" + listingId)
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspensionReason").value("Missing registration proof"))
                .andExpect(jsonPath("$.suspensionSource").value("ADMIN_REVIEW"))
                .andExpect(jsonPath("$.suspensionUntil").value("2026-06-15T10:30:00Z"));
    }

    @Test
    void hostListingDetail_returnsVehicleSuspensionMetadataForAutoSuspendedListing() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/host/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "SUSPENDED" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/host/listings/" + listingId)
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspensionReason").value("Vehicle moved to SUSPENDED"))
                .andExpect(jsonPath("$.suspensionSource").value("VEHICLE_STATUS_CHANGE"))
                .andExpect(jsonPath("$.suspensionUntil").doesNotExist());
    }

    @Test
    void nonSuspendedListingDetail_keepsSuspensionFieldsNull() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "Draft Listing");

        mockMvc.perform(get("/api/v1/host/listings/" + listingId)
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.suspensionReason").doesNotExist())
                .andExpect(jsonPath("$.suspensionSource").doesNotExist())
                .andExpect(jsonPath("$.suspensionUntil").doesNotExist());
    }

    @Test
    void resumeSuspendedListing_transitionsToActiveWhenVehicleActive() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(patch("/api/v1/host/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "SUSPENDED" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/host/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "ACTIVE" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/resume")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.suspensionReason").doesNotExist())
                .andExpect(jsonPath("$.suspensionSource").doesNotExist())
                .andExpect(jsonPath("$.suspensionUntil").doesNotExist());
    }

    @Test
    void resumeSuspendedListing_requiresVehicleActive() throws Exception {
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "My Listing");

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/submit")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/listings/" + listingId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/host/vehicles/" + vehicleId)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "SUSPENDED" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/host/listings/" + listingId + "/resume")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_ACTIVE"));
    }

    @Test
    void resumeSuspendedListing_rejectsWhenAnotherActiveListingExists() throws Exception {
        String vehicleId = createVehicle();
        String suspendedListing = createListing(vehicleId, "Suspended listing");
        String activeListing = createListing(vehicleId, "Active listing");

        mockMvc.perform(post("/api/v1/host/listings/" + suspendedListing + "/submit")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/listings/" + suspendedListing + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/listings/" + suspendedListing + "/suspend")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "manual suspend" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(post("/api/v1/host/listings/" + activeListing + "/submit")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/listings/" + activeListing + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/host/listings/" + suspendedListing + "/resume")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONE_ACTIVE_LISTING_PER_VEHICLE"));
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

    private JsonNode parseJson(MvcResult result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString());
    }
}
