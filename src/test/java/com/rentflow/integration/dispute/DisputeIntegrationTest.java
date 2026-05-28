package com.rentflow.integration.dispute;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.dispute.repository.DisputeRepository;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleCategory;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class DisputeIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private DisputeRepository disputeRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ObjectMapper objectMapper;

    private AuthUser host;
    private AuthUser customer;
    private AuthUser admin;
    private String customerToken;
    private String adminToken;
    private Listing listing;

    @BeforeEach
    void setUp() {
        disputeRepository.deleteAll();
        bookingRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        authUserRepository.deleteAll();

        host = saveUser("host-" + UUID.randomUUID() + "@example.com", Role.HOST);
        customer = saveUser("customer-" + UUID.randomUUID() + "@example.com", Role.CUSTOMER);
        admin = saveUser("admin-" + UUID.randomUUID() + "@example.com", Role.ADMIN);
        customerToken = jwtTokenProvider.generateAccessToken(customer.getId(), customer.getEmail(), List.of(Role.CUSTOMER));
        adminToken = jwtTokenProvider.generateAccessToken(admin.getId(), admin.getEmail(), List.of(Role.ADMIN));
        listing = saveListing(host, ListingStatus.ACTIVE);
    }

    @Test
    void customerCreatesDisputeAndAdminResolves() throws Exception {
        Booking completed = saveBooking(customer.getId(), host.getId(), listing.getId(), BookingStatus.COMPLETED);
        Booking confirmed = saveBooking(customer.getId(), host.getId(), listing.getId(), BookingStatus.CONFIRMED);

        mockMvc.perform(post("/api/v1/bookings/{id}/dispute", confirmed.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"cannot create yet"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_INVALID_STATUS"));

        String disputeBody = mockMvc.perform(post("/api/v1/bookings/{id}/dispute", completed.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"car condition issue"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode node = objectMapper.readTree(disputeBody);
        String disputeId = node.get("id").asText();

        mockMvc.perform(post("/api/v1/admin/disputes/{id}/resolve", disputeId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionNote":"partial refund approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        assertThat(disputeRepository.findAll()).hasSize(1);
        assertThat(disputeRepository.findAll().get(0).getResolutionNote()).contains("partial refund");
    }

    private AuthUser saveUser(String email, Role role) {
        AuthUser user = new AuthUser(email, "hash", UserStatus.ACTIVE, true);
        user.addRole(role);
        return authUserRepository.save(user);
    }

    private Listing saveListing(AuthUser hostUser, ListingStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setHostId(hostUser.getId());
        vehicle.setCategory(VehicleCategory.SEDAN);
        vehicle.setMake("Toyota");
        vehicle.setModel("Vios");
        vehicle.setManufactureYear(2022);
        vehicle.setTransmission(TransmissionType.AUTO);
        vehicle.setFuelType(FuelType.PETROL);
        vehicle.setSeats(5);
        vehicle.setCity("Hanoi");
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle = vehicleRepository.save(vehicle);

        Listing newListing = new Listing();
        newListing.setHostId(hostUser.getId());
        newListing.setVehicleId(vehicle.getId());
        newListing.setTitle("Dispute listing");
        newListing.setCity("Hanoi");
        newListing.setBasePricePerDay(new BigDecimal("700000.00"));
        newListing.setCurrency("VND");
        newListing.setInstantBook(true);
        newListing.setDailyKmLimit(200);
        newListing.setCancellationPolicy(CancellationPolicy.FLEXIBLE);
        newListing.setStatus(status);
        return listingRepository.save(newListing);
    }

    private Booking saveBooking(UUID customerId, UUID hostId, UUID listingId, BookingStatus status) {
        Booking booking = new Booking();
        booking.setCustomerId(customerId);
        booking.setHostId(hostId);
        booking.setListingId(listingId);
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setStatus(status);
        booking.setPriceSnapshot("""
                {"totalAmount":1400000.00,"currency":"VND","extras":[]}
                """);
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"FLEXIBLE","instantBook":true,"dailyKmLimit":200}
                """);
        return bookingRepository.save(booking);
    }
}
