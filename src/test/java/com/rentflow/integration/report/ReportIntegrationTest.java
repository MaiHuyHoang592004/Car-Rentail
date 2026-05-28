package com.rentflow.integration.report;

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
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.repository.BookingPaymentRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class ReportIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingPaymentRepository bookingPaymentRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private AuthUser admin;
    private AuthUser host;
    private AuthUser customer;
    private String adminToken;
    private String hostToken;
    private Listing listing;

    @BeforeEach
    void setUp() {
        bookingPaymentRepository.deleteAll();
        bookingRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        authUserRepository.deleteAll();

        admin = saveUser("admin-" + UUID.randomUUID() + "@example.com", Role.ADMIN);
        host = saveUser("host-" + UUID.randomUUID() + "@example.com", Role.HOST);
        customer = saveUser("customer-" + UUID.randomUUID() + "@example.com", Role.CUSTOMER);
        adminToken = jwtTokenProvider.generateAccessToken(admin.getId(), admin.getEmail(), List.of(Role.ADMIN));
        hostToken = jwtTokenProvider.generateAccessToken(host.getId(), host.getEmail(), List.of(Role.HOST));
        listing = saveListing(host, ListingStatus.ACTIVE);
    }

    @Test
    void adminRevenueAndHostEarningsReportUseCapturedBaseline() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), listing.getId(), BookingStatus.COMPLETED);
        saveCapturedPayment(booking.getId(), new BigDecimal("1400000.00"));

        mockMvc.perform(get("/api/v1/admin/reports/revenue")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCaptured").value(1400000.00))
                .andExpect(jsonPath("$.platformFeeAmount").value(210000.00))
                .andExpect(jsonPath("$.bookingCount").value(1));

        mockMvc.perform(get("/api/v1/host/reports/earnings")
                        .header("Authorization", "Bearer " + hostToken)
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossCaptured").value(1400000.00))
                .andExpect(jsonPath("$.netEarnings").value(1190000.00))
                .andExpect(jsonPath("$.bookingCount").value(1));
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
        newListing.setTitle("Report listing");
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

    private void saveCapturedPayment(UUID bookingId, BigDecimal capturedAmount) {
        BookingPayment payment = new BookingPayment();
        payment.setBookingId(bookingId);
        payment.setSelectedBankId(UUID.fromString("00000000-0000-0000-0000-000000000111"));
        payment.setPaymentMethod(PaymentMethod.COREBANK_TRANSFER);
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setAuthorizedAmount(capturedAmount);
        payment.setCapturedAmount(capturedAmount);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency("VND");
        payment.setExternalOrderRef("rentflow:booking:" + bookingId);
        payment.setProviderPaymentOrderId("payment-order-1");
        payment.setProviderHoldId("hold-1");
        payment.setProviderStatus("CAPTURED");
        bookingPaymentRepository.save(payment);
    }
}
