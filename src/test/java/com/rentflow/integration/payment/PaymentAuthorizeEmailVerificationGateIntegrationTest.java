package com.rentflow.integration.payment;

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
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentBankRepository;
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
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@TestPropertySource(properties = {
        "rentflow.payment.require-email-verification=true",
        "rentflow.payment.bank-transfer.account-number=1234567890",
        "rentflow.payment.bank-transfer.account-name=RENTFLOW ESCROW",
        "rentflow.payment.bank-transfer.transfer-content-prefix=RENTFLOW"
})
class PaymentAuthorizeEmailVerificationGateIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate PICKUP_DATE = LocalDate.of(2026, 6, 15);
    private static final LocalDate RETURN_DATE = LocalDate.of(2026, 6, 17);

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private BookingPaymentRepository bookingPaymentRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AvailabilityCalendarRepository availabilityRepository;
    @Autowired private PaymentBankRepository paymentBankRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private AuthUser host;
    private Listing listing;
    private PaymentBank bank;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
        bookingPaymentRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        availabilityRepository.deleteAll();
        bookingRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();

        host = saveUser("host", true, Role.HOST);
        listing = saveListing(host);
        bank = paymentBankRepository.findAll().stream()
                .filter(item -> "VCB".equals(item.getCode()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void authorizeWithUnverifiedCustomerReturnsForbidden() throws Exception {
        AuthUser unverifiedCustomer = saveUser("customer-unverified", false, Role.CUSTOMER);
        Booking booking = saveHeldBooking(unverifiedCustomer, host, listing);
        saveHeldAvailability(booking);

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/authorize", booking.getId())
                        .header("Authorization", "Bearer " + token(unverifiedCustomer, Role.CUSTOMER))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizeBody(bank.getId(), PaymentMethod.BANK_TRANSFER_QR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void authorizeWithVerifiedCustomerStillSucceeds() throws Exception {
        AuthUser verifiedCustomer = saveUser("customer-verified", true, Role.CUSTOMER);
        Booking booking = saveHeldBooking(verifiedCustomer, host, listing);
        saveHeldAvailability(booking);

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/authorize", booking.getId())
                        .header("Authorization", "Bearer " + token(verifiedCustomer, Role.CUSTOMER))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizeBody(bank.getId(), PaymentMethod.BANK_TRANSFER_QR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PENDING_TRANSFER"));
    }

    private AuthUser saveUser(String prefix, boolean emailVerified, Role role) {
        AuthUser user = new AuthUser(prefix + "-" + UUID.randomUUID() + "@example.com", "hash", UserStatus.ACTIVE, emailVerified);
        user.addRole(role);
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
        vehicle.setFuelType(FuelType.GASOLINE);
        vehicle.setSeats(5);
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle.setCity("Ho Chi Minh City");
        vehicle = vehicleRepository.save(vehicle);

        Listing savedListing = new Listing();
        savedListing.setVehicleId(vehicle.getId());
        savedListing.setHostId(hostUser.getId());
        savedListing.setTitle("Authorize payment fixture");
        savedListing.setDescription("Authorize payment fixture");
        savedListing.setCity("Ho Chi Minh City");
        savedListing.setAddress("1 Nguyen Hue");
        savedListing.setBasePricePerDay(new BigDecimal("700000.00"));
        savedListing.setCurrency("VND");
        savedListing.setDailyKmLimit(200);
        savedListing.setInstantBook(false);
        savedListing.setCancellationPolicy(CancellationPolicy.FLEXIBLE);
        savedListing.setStatus(ListingStatus.ACTIVE);
        return listingRepository.save(savedListing);
    }

    private Booking saveHeldBooking(AuthUser customerUser, AuthUser hostUser, Listing savedListing) {
        Booking savedBooking = new Booking();
        savedBooking.setCustomerId(customerUser.getId());
        savedBooking.setHostId(hostUser.getId());
        savedBooking.setListingId(savedListing.getId());
        savedBooking.setPickupDate(PICKUP_DATE);
        savedBooking.setReturnDate(RETURN_DATE);
        savedBooking.setStatus(BookingStatus.HELD);
        savedBooking.setHoldToken(UUID.randomUUID());
        savedBooking.setHoldExpiresAt(Instant.parse("2026-06-15T03:00:00Z"));
        savedBooking.setPriceSnapshot("""
                {"rentalDays":2,"basePricePerDay":700000.00,"baseAmount":1400000.00,"extras":[],"subtotal":1400000.00,"discountAmount":0.00,"serviceFee":0.00,"totalAmount":1400000.00,"currency":"VND"}
                """);
        savedBooking.setPolicySnapshot("""
                {"cancellationPolicy":"FLEXIBLE","instantBook":false,"dailyKmLimit":200}
                """);
        return bookingRepository.save(savedBooking);
    }

    private void saveHeldAvailability(Booking booking) {
        availabilityRepository.saveAll(booking.getPickupDate().datesUntil(booking.getReturnDate())
                .map(date -> {
                    AvailabilityCalendar row = new AvailabilityCalendar(booking.getListingId(), date);
                    row.setStatus(AvailabilityStatus.HOLD);
                    row.setBookingId(booking.getId());
                    row.setHoldToken(booking.getHoldToken());
                    row.setHoldExpiresAt(booking.getHoldExpiresAt());
                    return row;
                })
                .toList());
    }

    private String authorizeBody(UUID bankId, PaymentMethod paymentMethod) {
        return """
                {"bankId":"%s","paymentMethod":"%s"}
                """.formatted(bankId, paymentMethod.name());
    }
}
