package com.rentflow.integration.booking;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.availability.entity.AvailabilityCalendar;
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
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
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
class BookingEmailVerificationGateIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate PICKUP_DATE = LocalDate.of(2026, 6, 10);
    private static final LocalDate RETURN_DATE = LocalDate.of(2026, 6, 12);

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private BookingPaymentRepository bookingPaymentRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AvailabilityCalendarRepository availabilityRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private AuthUser host;
    private Listing listing;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
        bookingPaymentRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        bookingRepository.deleteAll();
        availabilityRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        host = saveUser("host", true, Role.HOST);
        listing = saveListing(host);
        saveAvailability(listing.getId(), PICKUP_DATE, RETURN_DATE, AvailabilityStatus.FREE);
    }

    @Test
    void createBookingWithUnverifiedEmailReturnsForbidden() throws Exception {
        AuthUser unverifiedCustomer = saveUser("customer-unverified", false, Role.CUSTOMER);
        String token = token(unverifiedCustomer, Role.CUSTOMER);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(listing.getId(), PICKUP_DATE, RETURN_DATE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        assertThat(bookingRepository.findAll()).isEmpty();
    }

    @Test
    void createBookingWithVerifiedEmailStillSucceeds() throws Exception {
        AuthUser verifiedCustomer = saveUser("customer-verified", true, Role.CUSTOMER);
        String token = token(verifiedCustomer, Role.CUSTOMER);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(listing.getId(), PICKUP_DATE, RETURN_DATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"));

        Booking booking = bookingRepository.findAll().stream().findFirst().orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
    }

    private AuthUser saveUser(String prefix, boolean emailVerified, Role... roles) {
        AuthUser user = new AuthUser(prefix + "-" + UUID.randomUUID() + "@example.com", "hash", UserStatus.ACTIVE, emailVerified);
        for (Role role : roles) {
            user.addRole(role);
        }
        return authUserRepository.save(user);
    }

    private String token(AuthUser user, Role... roles) {
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), List.of(roles));
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
        vehicle.setCity("Hanoi");
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle = vehicleRepository.save(vehicle);

        Listing newListing = new Listing();
        newListing.setHostId(hostUser.getId());
        newListing.setVehicleId(vehicle.getId());
        newListing.setTitle("Email gate listing");
        newListing.setCity("Hanoi");
        newListing.setBasePricePerDay(new BigDecimal("700000.00"));
        newListing.setCurrency("VND");
        newListing.setInstantBook(true);
        newListing.setDailyKmLimit(200);
        newListing.setCancellationPolicy(CancellationPolicy.FLEXIBLE);
        newListing.setStatus(ListingStatus.ACTIVE);
        return listingRepository.save(newListing);
    }

    private void saveAvailability(UUID listingId, LocalDate pickupDate, LocalDate returnDate, AvailabilityStatus status) {
        availabilityRepository.saveAll(pickupDate.datesUntil(returnDate)
                .map(date -> {
                    AvailabilityCalendar row = new AvailabilityCalendar(listingId, date);
                    row.setStatus(status);
                    return row;
                })
                .toList());
    }

    private String createBody(UUID listingId, LocalDate pickupDate, LocalDate returnDate) {
        return """
                {
                  "listingId":"%s",
                  "pickupDate":"%s",
                  "returnDate":"%s",
                  "pickupLocation":"Hanoi",
                  "returnLocation":"Hanoi",
                  "extras":[]
                }
                """.formatted(listingId, pickupDate, returnDate);
    }
}
