package com.rentflow.integration.availability;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class AvailabilityIntegrationTest extends BaseIntegrationTest {

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

        registerUser("admin@example.com", "Password@123", "ADMIN");
        adminToken = login("admin@example.com", "Password@123");
    }

    // ─── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("guest view: HOLD row maps to UNAVAILABLE, bookingId is not exposed")
    void publicView_holdRow_hiddenFromGuest() throws Exception {
        String listingId = createAndApproveListing();

        UUID bookingId = UUID.randomUUID();
        AvailabilityCalendar holdRow = new AvailabilityCalendar(
                UUID.fromString(listingId), LocalDate.of(2026, 7, 1));
        holdRow.setStatus(AvailabilityStatus.HOLD);
        holdRow.setBookingId(bookingId);
        availabilityRepository.save(holdRow);

        mockMvc.perform(get("/api/v1/listings/{listingId}/availability", listingId)
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability.['2026-07-01']").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.bookingId").doesNotExist());
    }

    @Test
    @DisplayName("host view: returns all statuses including bookingId for HOLD rows")
    void hostView_showsAllStatuses() throws Exception {
        String listingId = createAndApproveListing();
        LocalDate date = LocalDate.of(2026, 7, 1);
        UUID bookingId = UUID.randomUUID();

        // Insert FREE, HOLD, BOOKED, BLOCKED rows for the same date
        AvailabilityCalendar freeRow = new AvailabilityCalendar(UUID.fromString(listingId), date);
        freeRow.setStatus(AvailabilityStatus.FREE);
        availabilityRepository.save(freeRow);

        AvailabilityCalendar holdRow = new AvailabilityCalendar(UUID.fromString(listingId), date.plusDays(1));
        holdRow.setStatus(AvailabilityStatus.HOLD);
        holdRow.setBookingId(bookingId);
        availabilityRepository.save(holdRow);

        AvailabilityCalendar bookedRow = new AvailabilityCalendar(UUID.fromString(listingId), date.plusDays(2));
        bookedRow.setStatus(AvailabilityStatus.BOOKED);
        bookedRow.setBookingId(bookingId);
        availabilityRepository.save(bookedRow);

        AvailabilityCalendar blockedRow = new AvailabilityCalendar(UUID.fromString(listingId), date.plusDays(3));
        blockedRow.setStatus(AvailabilityStatus.BLOCKED);
        availabilityRepository.save(blockedRow);

        mockMvc.perform(get("/api/v1/host/listings/{id}/availability", listingId)
                        .header("Authorization", "Bearer " + hostToken)
                        .param("from", date.toString())
                        .param("to", date.plusDays(5).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dates").isArray())
                .andExpect(jsonPath("$.dates.length()").value(4))
                .andExpect(jsonPath("$.dates[*].status", hasItems("FREE", "HOLD", "BOOKED", "BLOCKED")))
                .andExpect(jsonPath("$.dates[?(@.status=='HOLD')].bookingId[0]").exists());
    }

    @Test
    @DisplayName("blockDates with HOLD date returns 409 AVAILABILITY_CONFLICT")
    void blockDates_holdDate_returns409() throws Exception {
        String listingId = createAndApproveListing();

        AvailabilityCalendar holdRow = new AvailabilityCalendar(
                UUID.fromString(listingId), LocalDate.of(2026, 7, 5));
        holdRow.setStatus(AvailabilityStatus.HOLD);
        availabilityRepository.save(holdRow);

        mockMvc.perform(post("/api/v1/host/listings/{id}/availability/block", listingId)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "dates": ["2026-07-05"] }
                            """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVAILABILITY_CONFLICT"));
    }

    @Test
    @DisplayName("blockDates with FREE date succeeds and updates DB row")
    void blockDates_freeDate_succeeds() throws Exception {
        String listingId = createAndApproveListing();
        LocalDate targetDate = LocalDate.of(2026, 7, 10);

        mockMvc.perform(post("/api/v1/host/listings/{id}/availability/block", listingId)
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "dates": ["2026-07-10"] }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1));

        // Verify DB row was updated to BLOCKED
        AvailabilityCalendar row = availabilityRepository
                .findById(new com.rentflow.availability.entity.AvailabilityId(
                        UUID.fromString(listingId), targetDate))
                .orElseThrow();
        assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.BLOCKED);
    }

    @Test
    @DisplayName("guest cannot access host availability view — returns 403")
    void guestCannotAccessHostView_returns403() throws Exception {
        String listingId = createAndApproveListing();

        // No auth header
        mockMvc.perform(get("/api/v1/host/listings/{id}/availability", listingId)
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-02"))
                .andExpect(status().isForbidden());
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
        user.getRoles().add(new UserRole(user, Role.valueOf(role)));
        authUserRepository.save(user);
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

    private String createAndApproveListing() throws Exception {
        // Create vehicle
        var vehicleResult = mockMvc.perform(
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
                                      "seats": 5
                                    }
                                    """.formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn();
        String vehicleId = parseJson(vehicleResult).get("id").asText();

        // Create listing
        var listingResult = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/host/listings")
                                .header("Authorization", "Bearer " + hostToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "vehicleId": "%s",
                                      "title": "Test Listing",
                                      "city": "Hanoi",
                                      "basePricePerDay": 500000
                                    }
                                    """.formatted(vehicleId)))
                .andExpect(status().isCreated())
                .andReturn();
        String listingId = parseJson(listingResult).get("id").asText();

        // Submit
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/host/listings/" + listingId + "/submit")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        // Approve (generates 365 availability rows)
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
