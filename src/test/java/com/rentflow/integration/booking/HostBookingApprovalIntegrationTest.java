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
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.provider.corebank.CoreBankPaymentClient;
import com.rentflow.payment.provider.corebank.CoreBankVoidHoldResponse;
import com.rentflow.payment.provider.corebank.CoreBankVoidHoldResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import com.rentflow.scheduler.ExpireHostApprovalsProcessor;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@TestPropertySource(properties = {
        "rentflow.scheduler.expire-held-bookings.enabled=false",
        "rentflow.scheduler.expire-host-approvals.enabled=false"
})
class HostBookingApprovalIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingPaymentRepository bookingPaymentRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private AvailabilityCalendarRepository availabilityRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ExpireHostApprovalsProcessor expireHostApprovalsProcessor;
    @MockBean private CoreBankPaymentClient coreBankPaymentClient;

    private AuthUser customer;
    private AuthUser host;
    private AuthUser otherHost;
    private String hostToken;
    private String otherHostToken;
    private String customerToken;
    private Listing listing;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        bookingPaymentRepository.deleteAll();
        availabilityRepository.deleteAll();
        bookingRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        authUserRepository.deleteAll();

        customer = saveUser("customer-" + UUID.randomUUID() + "@example.com", Role.CUSTOMER);
        host = saveUser("host-" + UUID.randomUUID() + "@example.com", Role.HOST);
        otherHost = saveUser("other-host-" + UUID.randomUUID() + "@example.com", Role.HOST);
        hostToken = token(host, Role.HOST);
        otherHostToken = token(otherHost, Role.HOST);
        customerToken = token(customer, Role.CUSTOMER);
        listing = saveListing(host, "Toyota Vios 2022");
    }

    @Test
    void hostListReturnsOnlyOwnBookings() throws Exception {
        savePendingHostApprovalBooking(host, listing, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));
        Listing otherListing = saveListing(otherHost, "Honda City 2023");
        savePendingHostApprovalBooking(otherHost, otherListing, LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 7));

        mockMvc.perform(get("/api/v1/host/bookings")
                        .header("Authorization", "Bearer " + hostToken)
                        .param("status", "PENDING_HOST_APPROVAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_HOST_APPROVAL"));
    }

    @Test
    void approveTransitionsBookingAndAvailability() throws Exception {
        Booking booking = savePendingHostApprovalBooking(host, listing, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));

        mockMvc.perform(post("/api/v1/host/bookings/{id}/approve", booking.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        AvailabilityCalendar first = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate())).orElseThrow();
        AvailabilityCalendar second = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate().plusDays(1))).orElseThrow();
        assertThat(List.of(first, second)).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.BOOKED));
    }

    @Test
    void rejectVoidsThenReleasesAvailability() throws Exception {
        Booking booking = savePendingHostApprovalBooking(host, listing, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));
        when(coreBankPaymentClient.voidHold(any())).thenReturn(new CoreBankVoidHoldResult(
                new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}"));

        mockMvc.perform(post("/api/v1/host/bookings/{id}/reject", booking.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.REJECTED);
        BookingPayment payment = bookingPaymentRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
        AvailabilityCalendar first = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate())).orElseThrow();
        AvailabilityCalendar second = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate().plusDays(1))).orElseThrow();
        assertThat(List.of(first, second)).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.FREE));
    }

    @Test
    void timeoutProcessorExpiresPendingHostApproval() {
        Booking booking = savePendingHostApprovalBooking(host, listing, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));
        booking.setHostApprovalExpiresAt(Instant.now().minusSeconds(60));
        bookingRepository.save(booking);
        when(coreBankPaymentClient.voidHold(any())).thenReturn(new CoreBankVoidHoldResult(
                new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}"));

        int processed = expireHostApprovalsProcessor.processBatch(100);

        assertThat(processed).isEqualTo(1);
        Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        AvailabilityCalendar first = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate())).orElseThrow();
        AvailabilityCalendar second = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate().plusDays(1))).orElseThrow();
        assertThat(List.of(first, second)).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.FREE));
    }

    @Test
    void timeoutProcessorMarksVoidRetryWhenProviderFails() {
        Booking booking = savePendingHostApprovalBooking(host, listing, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));
        booking.setHostApprovalExpiresAt(Instant.now().minusSeconds(60));
        bookingRepository.save(booking);
        doThrow(new RuntimeException("corebank unavailable"))
                .when(coreBankPaymentClient).voidHold(any());

        int processed = expireHostApprovalsProcessor.processBatch(100);

        assertThat(processed).isZero();
        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.PENDING_HOST_APPROVAL);

        BookingPayment updatedPayment = bookingPaymentRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(updatedPayment.isVoidRetryRequired()).isTrue();
        assertThat(updatedPayment.getVoidRetryCount()).isEqualTo(1);
        assertThat(updatedPayment.getVoidRetryNextAt()).isNotNull();
        assertThat(updatedPayment.getVoidRetryLastError()).contains("corebank unavailable");
        assertThat(updatedPayment.getProviderStatus()).isEqualTo("VOID_RETRY_REQUIRED");

        assertThat(paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(updatedPayment.getId()))
                .anySatisfy(tx -> {
                    assertThat(tx.getType()).isEqualTo(PaymentTransactionType.VOID);
                    assertThat(tx.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
                    assertThat(tx.getProviderErrorCode()).isEqualTo("PAYMENT_PROVIDER_ERROR");
                });

        AvailabilityCalendar first = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate())).orElseThrow();
        AvailabilityCalendar second = availabilityRepository.findById(new AvailabilityId(listing.getId(), booking.getPickupDate().plusDays(1))).orElseThrow();
        assertThat(List.of(first, second)).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.HOLD);
            assertThat(row.getBookingId()).isEqualTo(booking.getId());
        });
    }

    @Test
    void nonHostCannotAccessHostEndpoints() throws Exception {
        Booking booking = savePendingHostApprovalBooking(host, listing, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));

        mockMvc.perform(post("/api/v1/host/bookings/{id}/approve", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherHostGetsNotFoundWhenApprovingForeignBooking() throws Exception {
        Booking booking = savePendingHostApprovalBooking(host, listing, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));

        mockMvc.perform(post("/api/v1/host/bookings/{id}/approve", booking.getId())
                        .header("Authorization", "Bearer " + otherHostToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    private AuthUser saveUser(String email, Role role) {
        AuthUser user = new AuthUser(email, "hash", UserStatus.ACTIVE, true);
        user.addRole(role);
        return authUserRepository.save(user);
    }

    private String token(AuthUser user, Role... roles) {
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), List.of(roles));
    }

    private Listing saveListing(AuthUser hostUser, String title) {
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
        newListing.setTitle(title);
        newListing.setCity("Hanoi");
        newListing.setBasePricePerDay(new BigDecimal("700000.00"));
        newListing.setCurrency("VND");
        newListing.setInstantBook(false);
        newListing.setDailyKmLimit(200);
        newListing.setCancellationPolicy(CancellationPolicy.FLEXIBLE);
        newListing.setStatus(ListingStatus.ACTIVE);
        return listingRepository.save(newListing);
    }

    private Booking savePendingHostApprovalBooking(AuthUser bookingHost, Listing bookingListing, LocalDate pickupDate, LocalDate returnDate) {
        Booking booking = new Booking();
        booking.setCustomerId(customer.getId());
        booking.setHostId(bookingHost.getId());
        booking.setListingId(bookingListing.getId());
        booking.setPickupDate(pickupDate);
        booking.setReturnDate(returnDate);
        booking.setStatus(BookingStatus.PENDING_HOST_APPROVAL);
        booking.setHoldToken(UUID.randomUUID());
        booking.setHoldExpiresAt(null);
        booking.setHostApprovalExpiresAt(Instant.now().plusSeconds(3600));
        booking.setPriceSnapshot("""
                {"totalAmount":1500000.00,"currency":"VND","extras":[]}
                """);
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"FLEXIBLE","instantBook":false,"dailyKmLimit":200}
                """);
        Booking saved = bookingRepository.save(booking);

        availabilityRepository.saveAll(saved.getPickupDate().datesUntil(saved.getReturnDate())
                .map(date -> {
                    AvailabilityCalendar row = new AvailabilityCalendar(saved.getListingId(), date);
                    row.setStatus(AvailabilityStatus.HOLD);
                    row.setBookingId(saved.getId());
                    row.setHoldToken(saved.getHoldToken());
                    row.setHoldExpiresAt(saved.getHostApprovalExpiresAt());
                    return row;
                })
                .toList());

        BookingPayment payment = new BookingPayment();
        payment.setBookingId(saved.getId());
        payment.setSelectedBankId(UUID.fromString("00000000-0000-0000-0000-000000000111"));
        payment.setPaymentMethod(PaymentMethod.COREBANK_TRANSFER);
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAmount(new BigDecimal("1500000.00"));
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency("VND");
        payment.setExternalOrderRef("rentflow:booking:" + saved.getId());
        payment.setProviderPaymentOrderId("payment-order-1");
        payment.setProviderHoldId("hold-1");
        payment.setProviderStatus("AUTHORIZED");
        bookingPaymentRepository.save(payment);

        return saved;
    }
}
