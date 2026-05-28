package com.rentflow.integration.review;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.review.repository.ReviewRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class ReviewIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private AuthUser host;
    private AuthUser customer;
    private String customerToken;
    private Listing listing;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        bookingRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        authUserRepository.deleteAll();

        host = saveUser("host-" + UUID.randomUUID() + "@example.com", Role.HOST);
        customer = saveUser("customer-" + UUID.randomUUID() + "@example.com", Role.CUSTOMER);
        customerToken = jwtTokenProvider.generateAccessToken(customer.getId(), customer.getEmail(), List.of(Role.CUSTOMER));
        listing = saveListing(host, ListingStatus.ACTIVE);
    }

    @Test
    void completedBookingAllowsSingleReviewAndUpdatesAggregate() throws Exception {
        Booking completedBooking = saveBooking(customer.getId(), host.getId(), listing.getId(), BookingStatus.COMPLETED);
        Booking confirmedBooking = saveBooking(customer.getId(), host.getId(), listing.getId(), BookingStatus.CONFIRMED);

        mockMvc.perform(post("/api/v1/bookings/{id}/review", completedBooking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating":5,
                                  "content":"Excellent"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(completedBooking.getId().toString()))
                .andExpect(jsonPath("$.rating").value(5));

        mockMvc.perform(post("/api/v1/bookings/{id}/review", completedBooking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating":4,
                                  "content":"Second review"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/v1/bookings/{id}/review", confirmedBooking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating":4,
                                  "content":"Not completed yet"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_INVALID_STATUS"));

        Listing updatedListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(updatedListing.getReviewCount()).isEqualTo(1);
        assertThat(updatedListing.getAverageRating()).isEqualByComparingTo("5.00");

        mockMvc.perform(get("/api/v1/listings/{id}/reviews", listing.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.averageRating").value(5.00))
                .andExpect(jsonPath("$.reviewCount").value(1));
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
        newListing.setTitle("Review listing");
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
