package com.rentflow.integration.user;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.notification.repository.NotificationRepository;
import com.rentflow.scheduler.ExpireDriverVerificationsProcessor;
import com.rentflow.user.entity.DriverVerification;
import com.rentflow.user.entity.DriverVerificationStatus;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.DriverVerificationRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@TestPropertySource(properties = {
        "rentflow.booking.require-driver-verification=true",
        "rentflow.scheduler.expire-driver-verifications.enabled=false",
        "rentflow.scheduler.expire-held-bookings.enabled=false"
})
class DriverVerificationExpiryIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate PICKUP_DATE = LocalDate.of(2026, 7, 10);
    private static final LocalDate RETURN_DATE = LocalDate.of(2026, 7, 12);

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private DriverVerificationRepository driverVerificationRepository;
    @Autowired private ExpireDriverVerificationsProcessor expireDriverVerificationsProcessor;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private AvailabilityCalendarRepository availabilityRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    payment_transactions,
                    booking_payments,
                    booking_extras,
                    bookings,
                    availability_calendar,
                    idempotency_keys,
                    listings,
                    vehicles,
                    driver_verifications,
                    notifications,
                    user_profiles,
                    auth_users
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void processBatchExpiresOverduePendingAndApprovedAndSyncsProfile() {
        AuthUser pendingCustomer = saveUserWithProfile("pending", UserProfile.DriverVerificationStatus.PENDING, Role.CUSTOMER);
        AuthUser approvedCustomer = saveUserWithProfile("approved", UserProfile.DriverVerificationStatus.APPROVED, Role.CUSTOMER);
        AuthUser stillApproved = saveUserWithProfile("still-approved", UserProfile.DriverVerificationStatus.APPROVED, Role.CUSTOMER);

        DriverVerification pending = saveVerification(pendingCustomer.getId(), DriverVerificationStatus.PENDING, LocalDate.now().minusDays(1));
        DriverVerification approved = saveVerification(approvedCustomer.getId(), DriverVerificationStatus.APPROVED, LocalDate.now().minusDays(2));
        DriverVerification valid = saveVerification(stillApproved.getId(), DriverVerificationStatus.APPROVED, LocalDate.now().plusDays(10));

        int processed = expireDriverVerificationsProcessor.processBatch(100);

        assertThat(processed).isEqualTo(2);
        assertThat(driverVerificationRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(DriverVerificationStatus.EXPIRED);
        assertThat(driverVerificationRepository.findById(approved.getId()).orElseThrow().getStatus())
                .isEqualTo(DriverVerificationStatus.EXPIRED);
        assertThat(driverVerificationRepository.findById(valid.getId()).orElseThrow().getStatus())
                .isEqualTo(DriverVerificationStatus.APPROVED);

        assertThat(userProfileRepository.findByUserId(pendingCustomer.getId()).orElseThrow().getDriverVerificationStatus())
                .isEqualTo(UserProfile.DriverVerificationStatus.EXPIRED);
        assertThat(userProfileRepository.findByUserId(approvedCustomer.getId()).orElseThrow().getDriverVerificationStatus())
                .isEqualTo(UserProfile.DriverVerificationStatus.EXPIRED);
        assertThat(userProfileRepository.findByUserId(stillApproved.getId()).orElseThrow().getDriverVerificationStatus())
                .isEqualTo(UserProfile.DriverVerificationStatus.APPROVED);
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(
                pendingCustomer.getId(),
                org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(
                approvedCustomer.getId(),
                org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(
                stillApproved.getId(),
                org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements()).isZero();
    }

    @Test
    void expiredUserAfterProcessorCannotCreateBooking() throws Exception {
        AuthUser host = saveUserWithProfile("host", UserProfile.DriverVerificationStatus.NOT_SUBMITTED, Role.HOST);
        Listing listing = saveListing(host);
        saveAvailability(listing.getId(), PICKUP_DATE, RETURN_DATE, AvailabilityStatus.FREE);

        AuthUser customer = saveUserWithProfile("customer", UserProfile.DriverVerificationStatus.APPROVED, Role.CUSTOMER);
        saveVerification(customer.getId(), DriverVerificationStatus.APPROVED, LocalDate.now().minusDays(1));
        expireDriverVerificationsProcessor.processBatch(100);
        String customerToken = token(customer, Role.CUSTOMER);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(listing.getId(), PICKUP_DATE, RETURN_DATE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DRIVER_LICENSE_NOT_APPROVED"));
    }

    private AuthUser saveUserWithProfile(String prefix, UserProfile.DriverVerificationStatus verificationStatus, Role... roles) {
        return transactionTemplate.execute(tx -> {
            AuthUser user = new AuthUser(prefix + "-" + UUID.randomUUID() + "@example.com", "hash", UserStatus.ACTIVE, true);
            for (Role role : roles) {
                user.addRole(role);
            }
            user = authUserRepository.save(user);

            UserProfile profile = new UserProfile("User " + prefix);
            profile.setUser(user);
            profile.setDriverVerificationStatus(verificationStatus);
            userProfileRepository.save(profile);
            return user;
        });
    }

    private DriverVerification saveVerification(UUID customerId, DriverVerificationStatus status, LocalDate expiryDate) {
        DriverVerification verification = new DriverVerification();
        verification.setCustomerId(customerId);
        verification.setStatus(status);
        verification.setLicenseNumberEncrypted("enc");
        verification.setLicenseNumberHash("hash");
        verification.setLicenseExpiryDate(expiryDate);
        verification.setDocumentFileId(UUID.randomUUID());
        return driverVerificationRepository.save(verification);
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

        Listing listing = new Listing();
        listing.setHostId(hostUser.getId());
        listing.setVehicleId(vehicle.getId());
        listing.setTitle("Expiry gate listing");
        listing.setCity("Hanoi");
        listing.setBasePricePerDay(new BigDecimal("700000.00"));
        listing.setCurrency("VND");
        listing.setInstantBook(true);
        listing.setDailyKmLimit(200);
        listing.setCancellationPolicy(CancellationPolicy.FLEXIBLE);
        listing.setStatus(ListingStatus.ACTIVE);
        return listingRepository.save(listing);
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
