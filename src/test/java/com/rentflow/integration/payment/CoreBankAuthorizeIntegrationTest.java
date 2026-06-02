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
import com.rentflow.payment.entity.CustomerPaymentAccount;
import com.rentflow.payment.entity.PaymentBank;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.CustomerPaymentAccountRepository;
import com.rentflow.payment.repository.PaymentBankRepository;
import com.rentflow.payment.provider.corebank.CoreBankAuthorizeHoldResponse;
import com.rentflow.payment.provider.corebank.CoreBankAuthorizeHoldResult;
import com.rentflow.payment.provider.corebank.CoreBankPaymentClient;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@TestPropertySource(properties = {
        "rentflow.payment.corebank.payee-account-id=rentflow-escrow-corebank-account-id",
        "rentflow.payment.corebank.base-url=http://localhost:8081"
})
class CoreBankAuthorizeIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate PICKUP_DATE = LocalDate.of(2026, 6, 1);
    private static final LocalDate RETURN_DATE = LocalDate.of(2026, 6, 3);

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AvailabilityCalendarRepository availabilityRepository;
    @Autowired private PaymentBankRepository paymentBankRepository;
    @Autowired private BookingPaymentRepository bookingPaymentRepository;
    @Autowired private CustomerPaymentAccountRepository customerPaymentAccountRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CoreBankPaymentClient coreBankPaymentClient;

    private AuthUser customer;
    private AuthUser host;
    private String customerToken;

    @BeforeEach
    void setUp() {
        bookingPaymentRepository.deleteAll();
        customerPaymentAccountRepository.deleteAll();
        availabilityRepository.deleteAll();
        bookingRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        authUserRepository.deleteAll();

        customer = saveUser("customer", Role.CUSTOMER);
        host = saveUser("host", Role.HOST);
        customerToken = token(customer, Role.CUSTOMER);
        when(coreBankPaymentClient.authorizeHold(any())).thenReturn(new CoreBankAuthorizeHoldResult(
                new CoreBankAuthorizeHoldResponse("payment-order-1", "hold-1", "AUTHORIZED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"holdId\":\"hold-1\",\"status\":\"AUTHORIZED\"}"));
    }

    @Test
    void coreBankAuthorizeInstantBookConfirmsBookingAndStoresProviderRefs() throws Exception {
        Listing listing = saveListing(host);
        Booking booking = saveHeldBooking(customer, host, listing, true);
        saveHeldAvailability(listing.getId(), booking.getId(), booking.getHoldToken());
        saveCoreBankAccount(customer.getId());
        PaymentBank coreBank = coreBank();

        MvcResult result = postAuthorize(booking.getId(), uuidV4(), coreBank.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.payment.provider").value("COREBANK"))
                .andExpect(jsonPath("$.payment.providerPaymentOrderId").value("payment-order-1"))
                .andExpect(jsonPath("$.payment.providerHoldId").value("hold-1"))
                .andReturn();

        JsonNode json = json(result);
        UUID paymentId = UUID.fromString(json.get("payment").get("id").asText());
        BookingPayment payment = bookingPaymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.getProviderPaymentOrderId()).isEqualTo("payment-order-1");
        assertThat(payment.getProviderHoldId()).isEqualTo("hold-1");

        Booking reloadedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloadedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(availabilityRepository.findByListingIdAndAvailableDateRange(listing.getId(), PICKUP_DATE, RETURN_DATE))
                .allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.BOOKED));
    }

    @Test
    void coreBankAuthorizeManualApprovalKeepsAvailabilityOnHold() throws Exception {
        Listing listing = saveListing(host);
        Booking booking = saveHeldBooking(customer, host, listing, false);
        saveHeldAvailability(listing.getId(), booking.getId(), booking.getHoldToken());
        saveCoreBankAccount(customer.getId());
        PaymentBank coreBank = coreBank();

        postAuthorize(booking.getId(), uuidV4(), coreBank.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("PENDING_HOST_APPROVAL"))
                .andExpect(jsonPath("$.payment.status").value("AUTHORIZED"));

        Booking reloadedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloadedBooking.getStatus()).isEqualTo(BookingStatus.PENDING_HOST_APPROVAL);
        assertThat(availabilityRepository.findByListingIdAndAvailableDateRange(listing.getId(), PICKUP_DATE, RETURN_DATE))
                .allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.HOLD));
    }

    @Test
    void sameIdempotencyKeyAndBodyReplayCoreBankAuthorizeResponse() throws Exception {
        Listing listing = saveListing(host);
        Booking booking = saveHeldBooking(customer, host, listing, true);
        saveHeldAvailability(listing.getId(), booking.getId(), booking.getHoldToken());
        saveCoreBankAccount(customer.getId());
        PaymentBank coreBank = coreBank();
        String key = uuidV4();

        MvcResult first = postAuthorize(booking.getId(), key, coreBank.getId())
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = postAuthorize(booking.getId(), key, coreBank.getId())
                .andExpect(status().isOk())
                .andReturn();

        assertThat(json(second).get("payment").get("id").asText())
                .isEqualTo(json(first).get("payment").get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions postAuthorize(UUID bookingId, String idempotencyKey, UUID bankId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments/authorize", bookingId)
                .header("Authorization", "Bearer " + customerToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bankId":"%s","paymentMethod":"COREBANK_TRANSFER"}
                        """.formatted(bankId)));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private PaymentBank coreBank() {
        return paymentBankRepository.findAll().stream()
                .filter(bank -> "COREBANK".equals(bank.getCode()))
                .findFirst()
                .orElseThrow();
    }

    private void saveCoreBankAccount(UUID userId) {
        CustomerPaymentAccount account = new CustomerPaymentAccount();
        account.setUserId(userId);
        account.setProvider(PaymentProviderType.COREBANK);
        account.setProviderAccountId("payer-account-1");
        account.setProviderCustomerRef("customer-1");
        account.setActive(true);
        customerPaymentAccountRepository.save(account);
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
        savedListing.setDescription("CoreBank authorize fixture");
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

    private Booking saveHeldBooking(AuthUser customerUser, AuthUser hostUser, Listing savedListing, boolean instantBook) {
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
                {"cancellationPolicy":"FLEXIBLE","instantBook":%s,"dailyKmLimit":200}
                """.formatted(instantBook));
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

    private String uuidV4() {
        return UUID.randomUUID().toString();
    }
}
