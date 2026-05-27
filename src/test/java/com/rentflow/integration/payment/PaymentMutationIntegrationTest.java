package com.rentflow.integration.payment;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.entity.PaymentTransactionStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.provider.corebank.CoreBankCaptureHoldResponse;
import com.rentflow.payment.provider.corebank.CoreBankCaptureHoldResult;
import com.rentflow.payment.provider.corebank.CoreBankPaymentClient;
import com.rentflow.payment.provider.corebank.CoreBankRefundResponse;
import com.rentflow.payment.provider.corebank.CoreBankRefundResult;
import com.rentflow.payment.provider.corebank.CoreBankVoidHoldResponse;
import com.rentflow.payment.provider.corebank.CoreBankVoidHoldResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class PaymentMutationIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingPaymentRepository bookingPaymentRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @MockBean private CoreBankPaymentClient coreBankPaymentClient;

    private AuthUser customer;
    private AuthUser host;
    private AuthUser admin;
    private AuthUser stranger;
    private String customerToken;
    private String hostToken;
    private String adminToken;
    private String strangerToken;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
        bookingPaymentRepository.deleteAll();
        bookingRepository.deleteAll();
        authUserRepository.deleteAll();

        customer = saveUser("customer", Role.CUSTOMER);
        host = saveUser("host", Role.HOST);
        admin = saveUser("admin", Role.ADMIN);
        stranger = saveUser("stranger", Role.CUSTOMER);
        customerToken = token(customer, Role.CUSTOMER);
        hostToken = token(host, Role.HOST);
        adminToken = token(admin, Role.ADMIN);
        strangerToken = token(stranger, Role.CUSTOMER);
    }

    @Test
    void getPaymentDetailAllowsCustomerHostAdminAndHidesFromOthers() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), BookingStatus.CONFIRMED);
        BookingPayment payment = saveAuthorizedPayment(booking.getId());
        saveTransaction(payment.getId(), booking.getId(), PaymentTransactionType.AUTHORIZE, PaymentTransactionStatus.SUCCEEDED, new BigDecimal("1400000.00"));

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/payments", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.id").value(payment.getId().toString()));

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/payments", booking.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/payments", booking.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/payments", booking.getId())
                        .header("Authorization", "Bearer " + strangerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void captureByHostUpdatesPaymentAndReplaysByIdempotency() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), BookingStatus.CONFIRMED);
        BookingPayment payment = saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.captureHold(any())).thenReturn(new CoreBankCaptureHoldResult(
                new CoreBankCaptureHoldResponse("payment-order-1", "journal-1", "CAPTURED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"journalId\":\"journal-1\",\"status\":\"CAPTURED\"}"));

        String key = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/payments/{paymentId}/capture", payment.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1400000.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"))
                .andExpect(jsonPath("$.transactions[1].type").value("CAPTURE"));

        mockMvc.perform(post("/api/v1/payments/{paymentId}/capture", payment.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1400000.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"));

        BookingPayment updated = bookingPaymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(updated.getCapturedAmount()).isEqualByComparingTo("1400000.00");
    }

    @Test
    void voidByHostMarksPaymentVoided() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), BookingStatus.CONFIRMED);
        BookingPayment payment = saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.voidHold(any())).thenReturn(new CoreBankVoidHoldResult(
                new CoreBankVoidHoldResponse("hold-1", "VOIDED"),
                "{\"holdId\":\"hold-1\",\"status\":\"VOIDED\"}"));

        mockMvc.perform(post("/api/v1/payments/{paymentId}/void", payment.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("VOIDED"));

        BookingPayment updated = bookingPaymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.VOIDED);
    }

    @Test
    void customerCannotCaptureHostsBookingPayment() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), BookingStatus.CONFIRMED);
        BookingPayment payment = saveAuthorizedPayment(booking.getId());

        mockMvc.perform(post("/api/v1/payments/{paymentId}/capture", payment.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100000.00}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void refundByHostSupportsPartialAndFullRefundWithIdempotency() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), BookingStatus.CONFIRMED);
        BookingPayment payment = saveCapturedPayment(booking.getId());
        when(coreBankPaymentClient.refund(any())).thenReturn(new CoreBankRefundResult(
                new CoreBankRefundResponse("payment-order-1", "refund-journal-1", "PARTIALLY_REFUNDED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"refundJournalId\":\"refund-journal-1\",\"status\":\"PARTIALLY_REFUNDED\"}"));

        String key = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/payments/{paymentId}/refund", payment.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":400000.00,"reason":"Customer cancellation refund"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PARTIALLY_REFUNDED"));

        mockMvc.perform(post("/api/v1/payments/{paymentId}/refund", payment.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":400000.00,"reason":"Customer cancellation refund"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PARTIALLY_REFUNDED"));

        BookingPayment partiallyRefunded = bookingPaymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(partiallyRefunded.getRefundedAmount()).isEqualByComparingTo("400000.00");
        assertThat(partiallyRefunded.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void refundSameIdempotencyKeyDifferentBodyReturnsConflict() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), BookingStatus.CONFIRMED);
        BookingPayment payment = saveCapturedPayment(booking.getId());
        when(coreBankPaymentClient.refund(any())).thenReturn(new CoreBankRefundResult(
                new CoreBankRefundResponse("payment-order-1", "refund-journal-1", "PARTIALLY_REFUNDED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"refundJournalId\":\"refund-journal-1\",\"status\":\"PARTIALLY_REFUNDED\"}"));

        String key = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/payments/{paymentId}/refund", payment.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":200000.00,"reason":"Reason A"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments/{paymentId}/refund", payment.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":300000.00,"reason":"Reason B"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void reconciliationMismatchSetsReconciliationRequired() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), BookingStatus.CONFIRMED);
        BookingPayment payment = saveCapturedPayment(booking.getId());
        when(coreBankPaymentClient.findOrderByExternalOrderRef(any())).thenReturn("""
                {"paymentOrderId":"payment-order-1","holdId":"hold-1","status":"SETTLED","authorizedAmount":1400000.00,"capturedAmount":1300000.00,"refundedAmount":0.00,"currency":"VND"}
                """);

        mockMvc.perform(get("/api/v1/payments/{paymentId}/reconciliation", payment.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresReconciliation").value(true))
                .andExpect(jsonPath("$.mismatchFlags.amountMismatch").value(true));

        BookingPayment updated = bookingPaymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.RECONCILIATION_REQUIRED);
    }

    @Test
    void reconciliationMatchKeepsCurrentStatus() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), BookingStatus.CONFIRMED);
        BookingPayment payment = saveCapturedPayment(booking.getId());
        payment.setProviderStatus("CAPTURED");
        bookingPaymentRepository.save(payment);
        when(coreBankPaymentClient.findOrderByExternalOrderRef(any())).thenReturn("""
                {"paymentOrderId":"payment-order-1","holdId":"hold-1","status":"CAPTURED","authorizedAmount":1400000.00,"capturedAmount":1400000.00,"refundedAmount":0.00,"currency":"VND"}
                """);

        mockMvc.perform(get("/api/v1/payments/{paymentId}/reconciliation", payment.getId())
                        .header("Authorization", "Bearer " + hostToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresReconciliation").value(false));

        BookingPayment updated = bookingPaymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    private Booking saveBooking(UUID customerId, UUID hostId, BookingStatus status) {
        Booking booking = new Booking();
        booking.setCustomerId(customerId);
        booking.setHostId(hostId);
        booking.setListingId(UUID.randomUUID());
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

    private BookingPayment saveAuthorizedPayment(UUID bookingId) {
        BookingPayment payment = new BookingPayment();
        payment.setBookingId(bookingId);
        payment.setSelectedBankId(UUID.fromString("00000000-0000-0000-0000-000000000111"));
        payment.setPaymentMethod(PaymentMethod.COREBANK_TRANSFER);
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAmount(new BigDecimal("1400000.00"));
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency("VND");
        payment.setExternalOrderRef("rentflow:booking:" + bookingId);
        payment.setProviderPaymentOrderId("payment-order-1");
        payment.setProviderHoldId("hold-1");
        payment.setProviderStatus("AUTHORIZED");
        return bookingPaymentRepository.save(payment);
    }

    private BookingPayment saveCapturedPayment(UUID bookingId) {
        BookingPayment payment = saveAuthorizedPayment(bookingId);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAmount(new BigDecimal("1400000.00"));
        payment.setProviderStatus("CAPTURED");
        return bookingPaymentRepository.save(payment);
    }

    private void saveTransaction(
            UUID paymentId,
            UUID bookingId,
            PaymentTransactionType type,
            PaymentTransactionStatus status,
            BigDecimal amount) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBookingPaymentId(paymentId);
        transaction.setBookingId(bookingId);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setAmount(amount);
        transaction.setCurrency("VND");
        transaction.setProvider(PaymentProviderType.COREBANK);
        paymentTransactionRepository.save(transaction);
    }

    private AuthUser saveUser(String prefix, Role role) {
        AuthUser user = new AuthUser(prefix + "-" + UUID.randomUUID() + "@example.com", "hash", UserStatus.ACTIVE, true);
        user.addRole(role);
        return authUserRepository.save(user);
    }

    private String token(AuthUser user, Role... roles) {
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), List.of(roles));
    }
}
