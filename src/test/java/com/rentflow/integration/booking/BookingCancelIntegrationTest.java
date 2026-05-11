package com.rentflow.integration.booking;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityId;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.idempotency.repository.IdempotencyKeyRepository;
import com.rentflow.common.security.JwtTokenProvider;
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
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@TestPropertySource(properties = "rentflow.scheduler.expire-held-bookings.enabled=false")
class BookingCancelIntegrationTest extends BaseIntegrationTest {

    private static final String IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AvailabilityCalendarRepository availabilityRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private AuthUser customer;
    private AuthUser host;
    private AuthUser otherCustomer;
    private String customerToken;
    private String otherCustomerToken;
    private Listing listing;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
        availabilityRepository.deleteAll();
        bookingRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        authUserRepository.deleteAll();

        customer = saveUser("customer-" + UUID.randomUUID() + "@example.com", Role.CUSTOMER);
        host = saveUser("host-" + UUID.randomUUID() + "@example.com", Role.HOST);
        otherCustomer = saveUser("other-" + UUID.randomUUID() + "@example.com", Role.CUSTOMER);
        customerToken = jwtTokenProvider.generateAccessToken(customer.getId(), customer.getEmail(), List.of(Role.CUSTOMER));
        otherCustomerToken = jwtTokenProvider.generateAccessToken(
                otherCustomer.getId(),
                otherCustomer.getEmail(),
                List.of(Role.CUSTOMER));
        listing = saveListing(host);
    }

    @Test
    void cancelHeldBookingSucceedsAndReleasesAvailability() throws Exception {
        Booking booking = saveBooking(BookingStatus.HELD);
        saveHeldAvailability(booking);

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"<b>Change</b> of plan"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId().toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Change of plan"));

        Booking cancelled = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Change of plan");

        var first = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate())).orElseThrow();
        var second = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate().plusDays(1))).orElseThrow();
        assertThat(List.of(first, second)).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.FREE);
            assertThat(row.getBookingId()).isNull();
            assertThat(row.getHoldToken()).isNull();
            assertThat(row.getHoldExpiresAt()).isNull();
        });
    }

    @Test
    void cancelHeldBookingSameKeyReplays() throws Exception {
        Booking booking = saveBooking(BookingStatus.HELD);
        saveHeldAvailability(booking);
        String body = """
                {"reason":"Change of plan"}
                """;

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Change of plan"));
    }

    @Test
    void cancelConfirmedBookingReturnsInvalidStatus() throws Exception {
        Booking booking = saveBooking(BookingStatus.CONFIRMED);

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Change of plan"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_INVALID_STATUS"));
    }

    @Test
    void cancelByNonOwnerReturnsNotFound() throws Exception {
        Booking booking = saveBooking(BookingStatus.HELD);
        saveHeldAvailability(booking);

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + otherCustomerToken)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Change of plan"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void cancelMissingIdempotencyKeyReturnsRequired() throws Exception {
        Booking booking = saveBooking(BookingStatus.HELD);

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Change of plan"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    private AuthUser saveUser(String email, Role role) {
        AuthUser user = new AuthUser(email, "hash", UserStatus.ACTIVE, true);
        user.addRole(role);
        return authUserRepository.save(user);
    }

    private Listing saveListing(AuthUser hostUser) {
        Vehicle vehicle = new Vehicle();
        vehicle.setHostId(hostUser.getId());
        vehicle.setCategory(VehicleCategory.SEDAN);
        vehicle.setMake("Toyota");
        vehicle.setModel("Vios");
        vehicle.setManufactureYear(2022);
        vehicle.setTransmission(TransmissionType.AUTO);
        vehicle.setFuelType(FuelType.PETROL);
        vehicle.setSeats(5);
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle = vehicleRepository.save(vehicle);

        Listing newListing = new Listing();
        newListing.setHostId(hostUser.getId());
        newListing.setVehicleId(vehicle.getId());
        newListing.setTitle("Toyota Vios 2022");
        newListing.setCity("Hanoi");
        newListing.setBasePricePerDay(new BigDecimal("700000.00"));
        newListing.setCurrency("VND");
        newListing.setInstantBook(true);
        newListing.setCancellationPolicy(CancellationPolicy.FLEXIBLE);
        newListing.setStatus(ListingStatus.ACTIVE);
        return listingRepository.save(newListing);
    }

    private Booking saveBooking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setCustomerId(customer.getId());
        booking.setHostId(host.getId());
        booking.setListingId(listing.getId());
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setStatus(status);
        booking.setHoldToken(UUID.randomUUID());
        booking.setHoldExpiresAt(Instant.parse("2026-05-11T00:15:00Z"));
        booking.setPriceSnapshot("""
                {"totalAmount":1500000.00,"currency":"VND","extras":[]}
                """);
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"FLEXIBLE","instantBook":true,"dailyKmLimit":200}
                """);
        return bookingRepository.save(booking);
    }

    private void saveHeldAvailability(Booking booking) {
        AvailabilityCalendar first = new AvailabilityCalendar(listing.getId(), booking.getPickupDate());
        first.setStatus(AvailabilityStatus.HOLD);
        first.setBookingId(booking.getId());
        first.setHoldToken(booking.getHoldToken());
        first.setHoldExpiresAt(booking.getHoldExpiresAt());
        AvailabilityCalendar second = new AvailabilityCalendar(listing.getId(), booking.getPickupDate().plusDays(1));
        second.setStatus(AvailabilityStatus.HOLD);
        second.setBookingId(booking.getId());
        second.setHoldToken(booking.getHoldToken());
        second.setHoldExpiresAt(booking.getHoldExpiresAt());
        availabilityRepository.saveAll(List.of(first, second));
    }
}
