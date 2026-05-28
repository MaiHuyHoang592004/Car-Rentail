package com.rentflow.integration.ratelimit;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.RefreshTokenRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
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
import com.rentflow.user.repository.UserProfileRepository;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@TestPropertySource(properties = {
        "rentflow.rate-limit.enabled=true",
        "rentflow.rate-limit.login.limit=1",
        "rentflow.rate-limit.login.window=PT15M",
        "rentflow.rate-limit.booking.create-limit=1",
        "rentflow.rate-limit.booking.create-window=PT1H",
        "rentflow.scheduler.expire-held-bookings.enabled=false"
})
class AuthBookingRateLimitIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AvailabilityCalendarRepository availabilityRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired private BookingPaymentRepository bookingPaymentRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired(required = false) private StringRedisTemplate redisTemplate;

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
        refreshTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        userProfileRepository.deleteAllInBatch();
        authUserRepository.deleteAll();
        if (redisTemplate != null) {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        }

        host = saveUser("host-" + UUID.randomUUID() + "@example.com", true, Role.HOST);
        listing = saveListing(host);
    }

    @Test
    void loginFailedAttemptsHitRateLimitAndReturn429Contract() throws Exception {
        String email = "rate-limit-login-" + UUID.randomUUID() + "@example.com";
        registerUser(email, "Password@123", "Rate Limit Login");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "wrong-password"
                                }
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "wrong-password"
                                }
                                """.formatted(email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void bookingCreateBeyondLimitReturns429AndKeepsSingleBooking() throws Exception {
        AuthUser customer = saveUser("customer-" + UUID.randomUUID() + "@example.com", true, Role.CUSTOMER);
        String token = jwtTokenProvider.generateAccessToken(customer.getId(), customer.getEmail(), List.of(Role.CUSTOMER));

        LocalDate pickup1 = LocalDate.of(2026, 7, 10);
        LocalDate return1 = LocalDate.of(2026, 7, 12);
        LocalDate pickup2 = LocalDate.of(2026, 7, 13);
        LocalDate return2 = LocalDate.of(2026, 7, 15);
        saveAvailability(listing.getId(), pickup1, return1);
        saveAvailability(listing.getId(), pickup2, return2);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBookingBody(listing.getId(), pickup1, return1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBookingBody(listing.getId(), pickup2, return2)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    private void registerUser(String email, String password, String fullName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "fullName": "%s"
                                }
                                """.formatted(email, password, fullName)))
                .andExpect(status().isCreated());
    }

    private AuthUser saveUser(String email, boolean emailVerified, Role... roles) {
        AuthUser user = new AuthUser(email, "hash", UserStatus.ACTIVE, emailVerified);
        for (Role role : roles) {
            user.addRole(role);
        }
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
        vehicle.setCity("Hanoi");
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle = vehicleRepository.save(vehicle);

        Listing newListing = new Listing();
        newListing.setHostId(hostUser.getId());
        newListing.setVehicleId(vehicle.getId());
        newListing.setTitle("Rate limit listing");
        newListing.setCity("Hanoi");
        newListing.setBasePricePerDay(new BigDecimal("700000.00"));
        newListing.setCurrency("VND");
        newListing.setInstantBook(true);
        newListing.setDailyKmLimit(200);
        newListing.setCancellationPolicy(CancellationPolicy.FLEXIBLE);
        newListing.setStatus(ListingStatus.ACTIVE);
        return listingRepository.save(newListing);
    }

    private void saveAvailability(UUID listingId, LocalDate pickupDate, LocalDate returnDate) {
        availabilityRepository.saveAll(pickupDate.datesUntil(returnDate)
                .map(date -> {
                    AvailabilityCalendar row = new AvailabilityCalendar(listingId, date);
                    row.setStatus(AvailabilityStatus.FREE);
                    return row;
                })
                .toList());
    }

    private String createBookingBody(UUID listingId, LocalDate pickupDate, LocalDate returnDate) {
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
