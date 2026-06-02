package com.rentflow.integration.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
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
import org.springframework.test.web.servlet.MvcResult;

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
@TestPropertySource(properties = {
        "rentflow.payment.bank-transfer.account-number=1234567890",
        "rentflow.payment.bank-transfer.account-name=RENTFLOW ESCROW",
        "rentflow.payment.bank-transfer.transfer-content-prefix=RENTFLOW",
        "rentflow.payment.sandbox-transfer-confirmation.enabled=true"
})
class BookingPaymentAuthorizeIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate PICKUP_DATE = LocalDate.of(2026, 6, 1);
    private static final LocalDate RETURN_DATE = LocalDate.of(2026, 6, 3);

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AvailabilityCalendarRepository availabilityRepository;
    @Autowired private PaymentBankRepository paymentBankRepository;
    @Autowired private BookingPaymentRepository bookingPaymentRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ObjectMapper objectMapper;

    private AuthUser customer;
    private AuthUser otherCustomer;
    private AuthUser host;
    private String customerToken;
    private String otherCustomerToken;
    private Listing listing;
    private Booking booking;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
        bookingPaymentRepository.deleteAll();
        availabilityRepository.deleteAll();
        bookingRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        authUserRepository.deleteAll();

        customer = saveUser("customer", Role.CUSTOMER);
        otherCustomer = saveUser("other-customer", Role.CUSTOMER);
        host = saveUser("host", Role.HOST);
        customerToken = token(customer, Role.CUSTOMER);
        otherCustomerToken = token(otherCustomer, Role.CUSTOMER);
        listing = saveListing(host);
        booking = saveHeldBooking(customer, host, listing);
        saveHeldAvailability(listing.getId(), booking.getId(), booking.getHoldToken());
    }

    @Test
    void authorizeWithVietQrManualBankReturnsPendingTransferAndPersistsPayment() throws Exception {
        String key = uuidV4();
        PaymentBank bank = paymentBankRepository.findAll().stream()
                .filter(item -> "VCB".equals(item.getCode()))
                .findFirst()
                .orElseThrow();

        MvcResult result = postAuthorize(customerToken, booking.getId(), key, bank.getId(), PaymentMethod.BANK_TRANSFER_QR)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.id").value(booking.getId().toString()))
                .andExpect(jsonPath("$.booking.status").value("HELD"))
                .andExpect(jsonPath("$.payment.status").value("PENDING_TRANSFER"))
                .andExpect(jsonPath("$.payment.provider").value("VIETQR_MANUAL"))
                .andExpect(jsonPath("$.payment.externalOrderRef").value("rentflow:booking:" + booking.getId()))
                .andExpect(jsonPath("$.payment.transferInstruction.bankCode").value("VCB"))
                .andExpect(jsonPath("$.payment.transferInstruction.accountNumber").value("1234567890"))
                .andReturn();

        JsonNode json = json(result);
        UUID paymentId = UUID.fromString(json.get("payment").get("id").asText());

        BookingPayment payment = bookingPaymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING_TRANSFER);
        assertThat(payment.getProvider()).isEqualTo(PaymentProviderType.VIETQR_MANUAL);
        assertThat(payment.getAuthorizedAmount()).isEqualByComparingTo("0");
        assertThat(payment.getExternalOrderRef()).isEqualTo("rentflow:booking:" + booking.getId());

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(paymentId);
        assertThat(transactions).singleElement().satisfies(tx -> {
            assertThat(tx.getType()).isEqualTo(PaymentTransactionType.AUTHORIZE);
            assertThat(tx.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCEEDED);
            assertThat(tx.getAmount()).isEqualByComparingTo("0");
            assertThat(tx.getProvider()).isEqualTo(PaymentProviderType.VIETQR_MANUAL);
        });

        Booking reloadedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloadedBooking.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(availabilityRepository.findByListingIdAndAvailableDateRange(listing.getId(), PICKUP_DATE, RETURN_DATE))
                .allSatisfy(row -> {
                    assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.HOLD);
                    assertThat(row.getBookingId()).isEqualTo(booking.getId());
                });
    }

    @Test
    void authorizeRejectsInactiveBank() throws Exception {
        PaymentBank inactiveBank = new PaymentBank();
        inactiveBank.setCode("INACTIVE_AUTH_BANK");
        inactiveBank.setShortName("Inactive Auth Bank");
        inactiveBank.setFullName("Inactive Auth Bank");
        inactiveBank.setCountryCode("VN");
        inactiveBank.setPaymentMethod(PaymentMethod.BANK_TRANSFER_QR);
        inactiveBank.setProvider(PaymentProviderType.VIETQR_MANUAL);
        inactiveBank.setActive(false);
        inactiveBank.setDisplayOrder(2000);
        inactiveBank = paymentBankRepository.save(inactiveBank);

        postAuthorize(customerToken, booking.getId(), uuidV4(), inactiveBank.getId(), PaymentMethod.BANK_TRANSFER_QR)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void authorizeForAnotherCustomersBookingReturnsNotFound() throws Exception {
        PaymentBank bank = paymentBankRepository.findAll().stream()
                .filter(item -> "VCB".equals(item.getCode()))
                .findFirst()
                .orElseThrow();

        postAuthorize(otherCustomerToken, booking.getId(), uuidV4(), bank.getId(), PaymentMethod.BANK_TRANSFER_QR)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void sameIdempotencyKeyAndBodyReplaysSameResponse() throws Exception {
        String key = uuidV4();
        PaymentBank bank = paymentBankRepository.findAll().stream()
                .filter(item -> "VCB".equals(item.getCode()))
                .findFirst()
                .orElseThrow();

        MvcResult first = postAuthorize(customerToken, booking.getId(), key, bank.getId(), PaymentMethod.BANK_TRANSFER_QR)
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = postAuthorize(customerToken, booking.getId(), key, bank.getId(), PaymentMethod.BANK_TRANSFER_QR)
                .andExpect(status().isOk())
                .andReturn();

        assertThat(json(second).get("payment").get("id").asText())
                .isEqualTo(json(first).get("payment").get("id").asText());
        assertThat(json(second).get("payment").get("externalOrderRef").asText())
                .isEqualTo("rentflow:booking:" + booking.getId());
    }

    @Test
    void sameIdempotencyKeyDifferentBodyReturnsConflict() throws Exception {
        String key = uuidV4();
        PaymentBank bank = paymentBankRepository.findAll().stream()
                .filter(item -> "VCB".equals(item.getCode()))
                .findFirst()
                .orElseThrow();
        PaymentBank coreBank = paymentBankRepository.findAll().stream()
                .filter(item -> "COREBANK".equals(item.getCode()))
                .findFirst()
                .orElseThrow();

        postAuthorize(customerToken, booking.getId(), key, bank.getId(), PaymentMethod.BANK_TRANSFER_QR)
                .andExpect(status().isOk());

        postAuthorize(customerToken, booking.getId(), key, coreBank.getId(), PaymentMethod.COREBANK_TRANSFER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    private org.springframework.test.web.servlet.ResultActions postAuthorize(
            String token,
            UUID bookingId,
            String idempotencyKey,
            UUID bankId,
            PaymentMethod paymentMethod) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/authorize", bookingId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bankId":"%s","paymentMethod":"%s"}
                        """.formatted(bankId, paymentMethod.name())));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private AuthUser saveUser(String prefix, Role role) {
        AuthUser user = new AuthUser(prefix + "-" + UUID.randomUUID() + "@example.com", "hash", UserStatus.ACTIVE, true);
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
        savedListing.setTitle("Toyota Vios 2022");
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
        savedBooking.setHoldExpiresAt(Instant.now().plusSeconds(3600));
        savedBooking.setPriceSnapshot("""
                {"totalAmount":1400000.00,"currency":"VND","extras":[]}
                """);
        savedBooking.setPolicySnapshot("""
                {"cancellationPolicy":"FLEXIBLE","instantBook":false,"dailyKmLimit":200}
                """);
        return bookingRepository.save(savedBooking);
    }

    private void saveHeldAvailability(UUID listingId, UUID bookingId, UUID holdToken) {
        for (LocalDate date = PICKUP_DATE; date.isBefore(RETURN_DATE); date = date.plusDays(1)) {
            AvailabilityCalendar row = new AvailabilityCalendar();
            row.setListingId(listingId);
            row.setAvailableDate(date);
            row.setStatus(AvailabilityStatus.HOLD);
            row.setBookingId(bookingId);
            row.setHoldToken(holdToken);
            row.setHoldExpiresAt(Instant.now().plusSeconds(3600));
            availabilityRepository.save(row);
        }
    }

    @Test
    void simulateTransferConfirmationForManualBookingAuthorizesPaymentAndWaitsForHost() throws Exception {
        PaymentBank bank = paymentBankRepository.findAll().stream()
                .filter(item -> "VCB".equals(item.getCode()))
                .findFirst()
                .orElseThrow();
        postAuthorize(customerToken, booking.getId(), uuidV4(), bank.getId(), PaymentMethod.BANK_TRANSFER_QR)
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/simulate-transfer-confirmation", booking.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("PENDING_HOST_APPROVAL"))
                .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.payment.authorizedAmount").value(1400000.00))
                .andExpect(jsonPath("$.payment.provider").value("VIETQR_MANUAL"))
                .andExpect(jsonPath("$.payment.providerStatus").value("SANDBOX_TRANSFER_CONFIRMED"));

        BookingPayment payment = bookingPaymentRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.getAuthorizedAmount()).isEqualByComparingTo("1400000.00");
        assertThat(payment.getProviderStatus()).isEqualTo("SANDBOX_TRANSFER_CONFIRMED");
        assertThat(payment.getProviderMetadata()).contains("\"sandbox\": true");

        Booking reloadedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloadedBooking.getStatus()).isEqualTo(BookingStatus.PENDING_HOST_APPROVAL);
        assertThat(reloadedBooking.getHoldExpiresAt()).isNull();
        assertThat(reloadedBooking.getHostApprovalExpiresAt()).isNotNull();
        assertThat(availabilityRepository.findByListingIdAndAvailableDateRange(listing.getId(), PICKUP_DATE, RETURN_DATE))
                .allSatisfy(row -> {
                    assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.HOLD);
                    assertThat(row.getBookingId()).isEqualTo(booking.getId());
                    assertThat(row.getHoldExpiresAt()).isEqualTo(reloadedBooking.getHostApprovalExpiresAt());
                });

        assertThat(paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId()))
                .anySatisfy(tx -> {
                    assertThat(tx.getType()).isEqualTo(PaymentTransactionType.AUTHORIZE);
                    assertThat(tx.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCEEDED);
                    assertThat(tx.getAmount()).isEqualByComparingTo("1400000.00");
                    assertThat(tx.getProviderResponse()).contains("\"sandbox\": true");
                });
    }

    @Test
    void simulateTransferConfirmationForInstantBookingConfirmsAndBooksAvailability() throws Exception {
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"FLEXIBLE","instantBook":true,"dailyKmLimit":200}
                """);
        bookingRepository.save(booking);
        PaymentBank bank = paymentBankRepository.findAll().stream()
                .filter(item -> "VCB".equals(item.getCode()))
                .findFirst()
                .orElseThrow();
        postAuthorize(customerToken, booking.getId(), uuidV4(), bank.getId(), PaymentMethod.BANK_TRANSFER_QR)
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/simulate-transfer-confirmation", booking.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"));

        Booking reloadedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloadedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reloadedBooking.getHoldToken()).isNull();
        assertThat(reloadedBooking.getHoldExpiresAt()).isNull();
        assertThat(availabilityRepository.findByListingIdAndAvailableDateRange(listing.getId(), PICKUP_DATE, RETURN_DATE))
                .allSatisfy(row -> {
                    assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.BOOKED);
                    assertThat(row.getBookingId()).isEqualTo(booking.getId());
                    assertThat(row.getHoldToken()).isNull();
                    assertThat(row.getHoldExpiresAt()).isNull();
                });
    }

    @Test
    void simulateTransferConfirmationForAnotherCustomerReturnsNotFound() throws Exception {
        PaymentBank bank = paymentBankRepository.findAll().stream()
                .filter(item -> "VCB".equals(item.getCode()))
                .findFirst()
                .orElseThrow();
        postAuthorize(customerToken, booking.getId(), uuidV4(), bank.getId(), PaymentMethod.BANK_TRANSFER_QR)
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/simulate-transfer-confirmation", booking.getId())
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void simulateTransferConfirmationRejectsExpiredHold() throws Exception {
        PaymentBank bank = paymentBankRepository.findAll().stream()
                .filter(item -> "VCB".equals(item.getCode()))
                .findFirst()
                .orElseThrow();
        postAuthorize(customerToken, booking.getId(), uuidV4(), bank.getId(), PaymentMethod.BANK_TRANSFER_QR)
                .andExpect(status().isOk());
        booking.setHoldExpiresAt(Instant.now().minusSeconds(60));
        bookingRepository.save(booking);

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/simulate-transfer-confirmation", booking.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_HOLD_EXPIRED"));
    }

    private String uuidV4() {
        return UUID.randomUUID().toString();
    }
}
