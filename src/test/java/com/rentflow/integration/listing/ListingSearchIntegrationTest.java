package com.rentflow.integration.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class ListingSearchIntegrationTest extends BaseIntegrationTest {

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
        login("admin@example.com", "Password@123"); // adminToken not needed separately
        adminToken = login("admin@example.com", "Password@123");
    }

    // ─── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("only ACTIVE listings returned in search")
    void search_onlyActiveListings_returnsActiveOnly() throws Exception {
        String vehicleId1 = createVehicle();
        String vehicleId2 = createVehicle();
        String vehicleId3 = createVehicle();

        String listingIdDraft = createListing(vehicleId1, "Draft Listing");
        String listingIdActive = createListingAndApprove(vehicleId2, "Active Listing");
        String listingIdSuspended = createListingAndApprove(vehicleId3, "Suspended Listing");

        // Suspend the third listing via admin
        mockMvc.perform(post("/api/v1/admin/listings/" + listingIdSuspended + "/suspend")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "reason": "Policy violation" }
                            """))
                .andExpect(status().isOk());

        // Search without date filter — DRAFT and SUSPENDED must not appear
        mockMvc.perform(get("/api/v1/listings")
                        .param("city", "Hanoi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(listingIdActive));
    }

    @Test
    @DisplayName("listings with HOLD on requested dates are excluded")
    void search_withDates_excludesHoldDates() throws Exception {
        String vehicleA = createVehicle();
        String vehicleB = createVehicle();

        String listingIdA = createListingAndApprove(vehicleA, "Listing A");
        String listingIdB = createListingAndApprove(vehicleB, "Listing B");

        // Insert HOLD row for listing A on 2026-07-01
        AvailabilityCalendar holdRow = new AvailabilityCalendar(
                UUID.fromString(listingIdA),
                java.time.LocalDate.of(2026, 7, 1));
        holdRow.setStatus(AvailabilityStatus.HOLD);
        availabilityRepository.save(holdRow);

        mockMvc.perform(get("/api/v1/listings")
                        .param("pickupDate", "2026-07-01")
                        .param("returnDate", "2026-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(listingIdB));
    }

    @Test
    @DisplayName("listings without availability rows are excluded when dates are provided")
    void search_missingAvailabilityRow_excludesListing() throws Exception {
        // Create listing in DRAFT — no availability rows are generated
        String vehicleId = createVehicle();
        String listingId = createListing(vehicleId, "No Availability Listing");

        // Search with dates — unapproved listing must not appear
        mockMvc.perform(get("/api/v1/listings")
                        .param("pickupDate", "2026-07-01")
                        .param("returnDate", "2026-07-02")
                        .param("city", "Hanoi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("pagination returns correct page metadata")
    void search_pagination_worksCorrectly() throws Exception {
        // Create 25 ACTIVE listings
        for (int i = 0; i < 25; i++) {
            String vehicleId = createVehicle();
            createListingAndApprove(vehicleId, "Listing " + i);
        }

        mockMvc.perform(get("/api/v1/listings")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.pageNumber").value(0));
    }

    @Test
    @DisplayName("guest can search listings without authentication")
    void search_guestCanSearch_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/listings"))
                .andExpect(status().isOk());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private JsonNode registerUser(String email, String password, String role) throws Exception {
        var result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register")
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
        var result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
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
        var result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/host/vehicles")
                                .header("Authorization", "Bearer " + hostToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "category": "SEDAN",
                                      "make": "Toyota",
                                      "model": "Camry",
                                      "year": 2020,
                                      "plateNumber": "%s",
                                      "transmission": "AUTO",
                                      "fuelType": "PETROL",
                                      "seats": 5,
                                      "city": "Hanoi"
                                    }
                                    """.formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn();
        return parseJson(result).get("id").asText();
    }

    private String createListing(String vehicleId, String title) throws Exception {
        var result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/host/listings")
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

    private String createListingAndApprove(String vehicleId, String title) throws Exception {
        String listingId = createListing(vehicleId, title);
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/host/listings/" + listingId + "/submit")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/admin/listings/" + listingId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return listingId;
    }

    private JsonNode parseJson(
            org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString());
    }
}
