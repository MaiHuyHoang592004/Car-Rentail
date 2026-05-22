package com.rentflow.integration.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.rentflow.booking.repository.BookingExtraRepository;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.idempotency.entity.IdempotencyStatus;
import com.rentflow.common.idempotency.repository.IdempotencyKeyRepository;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ExtraRepository;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.scheduler.ExpireHeldBookingsProcessor;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@TestPropertySource(properties = "rentflow.scheduler.expire-held-bookings.enabled=false")
class BookingPhase5IntegrationTest extends BaseIntegrationTest {

    private static final LocalDate PICKUP_DATE = LocalDate.of(2026, 6, 1);
    private static final LocalDate RETURN_DATE = LocalDate.of(2026, 6, 3);

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private ExtraRepository extraRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingExtraRepository bookingExtraRepository;
    @Autowired private AvailabilityCalendarRepository availabilityRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ExpireHeldBookingsProcessor expireHeldBookingsProcessor;
    @Autowired private ObjectMapper objectMapper;

    private AuthUser customer;
    private AuthUser otherCustomer;
    private AuthUser host;
    private AuthUser otherHost;
    private AuthUser admin;
    private String customerToken;
    private String otherCustomerToken;
    private String hostToken;
    private String otherHostToken;
    private String adminToken;
    private Listing listing;
    private Listing otherHostListing;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
        bookingExtraRepository.deleteAll();
        availabilityRepository.deleteAll();
        bookingRepository.deleteAll();
        extraRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        authUserRepository.deleteAll();

        customer = saveUser("customer", Role.CUSTOMER);
        otherCustomer = saveUser("other-customer", Role.CUSTOMER);
        host = saveUser("host", Role.HOST);
        otherHost = saveUser("other-host", Role.HOST);
        admin = saveUser("admin", Role.ADMIN);

        customerToken = token(customer, Role.CUSTOMER);
        otherCustomerToken = token(otherCustomer, Role.CUSTOMER);
        hostToken = token(host, Role.HOST);
        otherHostToken = token(otherHost, Role.HOST);
        adminToken = token(admin, Role.ADMIN);

        listing = saveListing(host, "Toyota Vios 2022");
        otherHostListing = saveListing(otherHost, "Honda City 2023");
    }

    @Test
    void createBookingSuccessCreatesHeldBookingAndHoldsAvailability() throws Exception {
        saveAvailability(listing.getId(), PICKUP_DATE, RETURN_DATE, AvailabilityStatus.FREE);

        MvcResult result = postCreate(customerToken, uuidV4(), createBody(listing.getId(), PICKUP_DATE, RETURN_DATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andReturn();

        UUID bookingId = UUID.fromString(json(result).get("id").asText());
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);

        List<AvailabilityCalendar> rows = availabilityRepository.findByListingIdAndAvailableDateRange(
                listing.getId(), PICKUP_DATE, RETURN_DATE);
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.HOLD);
            assertThat(row.getBookingId()).isEqualTo(bookingId);
        });
    }

    @Test
    void sameIdempotencyKeyAndSameBodyReturnsSameResponse() throws Exception {
        saveAvailability(listing.getId(), PICKUP_DATE, RETURN_DATE, AvailabilityStatus.FREE);
        String key = uuidV4();
        String body = createBody(listing.getId(), PICKUP_DATE, RETURN_DATE);

        MvcResult first = postCreate(customerToken, key, body).andExpect(status().isCreated()).andReturn();
        MvcResult second = postCreate(customerToken, key, body).andExpect(status().isCreated()).andReturn();

        assertThat(json(second).get("id").asText()).isEqualTo(json(first).get("id").asText());
        assertThat(json(second).get("status").asText()).isEqualTo("HELD");
    }

    @Test
    void sameIdempotencyKeyWithDifferentBodyReturnsConflict() throws Exception {
        saveAvailability(listing.getId(), PICKUP_DATE, RETURN_DATE.plusDays(1), AvailabilityStatus.FREE);
        String key = uuidV4();

        postCreate(customerToken, key, createBody(listing.getId(), PICKUP_DATE, RETURN_DATE))
                .andExpect(status().isCreated());

        postCreate(customerToken, key, createBody(listing.getId(), PICKUP_DATE.plusDays(1), RETURN_DATE.plusDays(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void missingAndInvalidIdempotencyKeyReturnBadRequest() throws Exception {
        String body = createBody(listing.getId(), PICKUP_DATE, RETURN_DATE);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.fromString("00000000-0000-1000-8000-000000000000"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void customerOverlapReturnsBookingOverlapCustomer() throws Exception {
        saveBooking(customer, host, listing, BookingStatus.HELD, PICKUP_DATE, RETURN_DATE);
        saveAvailability(listing.getId(), PICKUP_DATE.plusDays(1), RETURN_DATE.plusDays(1), AvailabilityStatus.FREE);

        postCreate(customerToken, uuidV4(), createBody(listing.getId(), PICKUP_DATE.plusDays(1), RETURN_DATE.plusDays(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_OVERLAP_CUSTOMER"));
    }

    @Test
    void selfBookingReturnsAccessDenied() throws Exception {
        AuthUser hostCustomer = saveUser("host-customer", Role.CUSTOMER, Role.HOST);
        String hostCustomerToken = token(hostCustomer, Role.CUSTOMER, Role.HOST);
        Listing selfListing = saveListing(hostCustomer, "Self hosted car");
        saveAvailability(selfListing.getId(), PICKUP_DATE, RETURN_DATE, AvailabilityStatus.FREE);

        postCreate(hostCustomerToken, uuidV4(), createBody(selfListing.getId(), PICKUP_DATE, RETURN_DATE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void failedCreateMarksIdempotencyKeyFailedEvenWhenBookingTransactionRollsBack() throws Exception {
        AuthUser hostCustomer = saveUser("host-customer", Role.CUSTOMER, Role.HOST);
        String hostCustomerToken = token(hostCustomer, Role.CUSTOMER, Role.HOST);
        Listing selfListing = saveListing(hostCustomer, "Self hosted car");
        saveAvailability(selfListing.getId(), PICKUP_DATE, RETURN_DATE, AvailabilityStatus.FREE);
        String key = uuidV4();

        postCreate(hostCustomerToken, key, createBody(selfListing.getId(), PICKUP_DATE, RETURN_DATE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(bookingRepository.findAll())
                .filteredOn(booking -> booking.getCustomerId().equals(hostCustomer.getId()))
                .filteredOn(booking -> booking.getListingId().equals(selfListing.getId()))
                .isEmpty();
        assertThat(idempotencyKeyRepository.findAll())
                .filteredOn(row -> row.getUserId().equals(hostCustomer.getId()))
                .filteredOn(row -> row.getScope() == IdempotencyScope.CREATE_BOOKING)
                .filteredOn(row -> row.getKey().equals(key.toLowerCase()))
                .singleElement()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo(IdempotencyStatus.FAILED));
    }

    @Test
    void nonFreeAvailabilityReturnsListingNotAvailable() throws Exception {
        saveAvailability(listing.getId(), PICKUP_DATE, RETURN_DATE, AvailabilityStatus.FREE);
        AvailabilityCalendar row = availabilityRepository
                .findById(new AvailabilityId(listing.getId(), PICKUP_DATE.plusDays(1)))
                .orElseThrow();
        row.setStatus(AvailabilityStatus.BLOCKED);
        availabilityRepository.save(row);

        postCreate(customerToken, uuidV4(), createBody(listing.getId(), PICKUP_DATE, RETURN_DATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_AVAILABLE"));
    }

    @Test
    void missingAvailabilityReturnsListingNotAvailable() throws Exception {
        saveAvailability(listing.getId(), PICKUP_DATE, PICKUP_DATE.plusDays(1), AvailabilityStatus.FREE);

        postCreate(customerToken, uuidV4(), createBody(listing.getId(), PICKUP_DATE, RETURN_DATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_AVAILABLE"));
    }

    @Test
    void expiryProcessorExpiresHeldBookingAndReleasesAvailability() {
        Booking booking = saveBooking(customer, host, listing, BookingStatus.HELD, PICKUP_DATE, RETURN_DATE);
        UUID holdToken = UUID.randomUUID();
        booking.setHoldToken(holdToken);
        booking.setHoldExpiresAt(Instant.now().minusSeconds(60));
        bookingRepository.save(booking);
        saveHeldAvailability(booking);

        int processed = expireHeldBookingsProcessor.processBatch(100);

        assertThat(processed).isEqualTo(1);
        Booking expired = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(availabilityRepository.findByListingIdAndAvailableDateRange(listing.getId(), PICKUP_DATE, RETURN_DATE))
                .allSatisfy(row -> {
                    assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.FREE);
                    assertThat(row.getBookingId()).isNull();
                    assertThat(row.getHoldToken()).isNull();
                    assertThat(row.getHoldExpiresAt()).isNull();
                });
    }

    @Test
    void patchLocationSucceedsAndRejectsUnknownAndDateFields() throws Exception {
        Booking booking = saveBooking(customer, host, listing, BookingStatus.HELD, PICKUP_DATE, RETURN_DATE);

        mockMvc.perform(patch("/api/v1/bookings/{id}", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupLocation":"New pickup","returnLocation":"New return"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupLocation").value("New pickup"))
                .andExpect(jsonPath("$.returnLocation").value("New return"));

        mockMvc.perform(patch("/api/v1/bookings/{id}", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"totalAmount":123}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/v1/bookings/{id}", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupDate":"2026-06-02","returnDate":"2026-06-04"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void bookingReadSecurityAllowsOwnerHostAndAdminOnly() throws Exception {
        Booking booking = saveBooking(customer, host, listing, BookingStatus.HELD, PICKUP_DATE, RETURN_DATE);

        mockMvc.perform(get("/api/v1/bookings/{id}", booking.getId())
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/bookings/{id}", booking.getId())
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId().toString()));

        mockMvc.perform(get("/api/v1/bookings/{id}", booking.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId().toString()));

        mockMvc.perform(get("/api/v1/bookings/{id}", booking.getId())
                        .header("Authorization", "Bearer " + otherHostToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void overlapQueryUsesActiveStatusesAndHalfOpenRanges() {
        List<BookingStatus> activeStatuses = List.of(
                BookingStatus.HELD,
                BookingStatus.PENDING_HOST_APPROVAL,
                BookingStatus.CONFIRMED);

        for (BookingStatus status : activeStatuses) {
            bookingRepository.deleteAll();
            saveBooking(customer, host, listing, status, PICKUP_DATE, RETURN_DATE);

            assertThat(bookingRepository.existsOverlappingActiveBooking(
                    customer.getId(),
                    PICKUP_DATE.plusDays(1),
                    RETURN_DATE.plusDays(1),
                    activeStatuses)).isTrue();
        }

        for (BookingStatus status : List.of(BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.EXPIRED)) {
            bookingRepository.deleteAll();
            saveBooking(customer, host, listing, status, PICKUP_DATE, RETURN_DATE);

            assertThat(bookingRepository.existsOverlappingActiveBooking(
                    customer.getId(),
                    PICKUP_DATE.plusDays(1),
                    RETURN_DATE.plusDays(1),
                    activeStatuses)).isFalse();
        }

        bookingRepository.deleteAll();
        saveBooking(customer, host, listing, BookingStatus.HELD, PICKUP_DATE, RETURN_DATE);
        assertThat(bookingRepository.existsOverlappingActiveBooking(
                customer.getId(), RETURN_DATE, RETURN_DATE.plusDays(2), activeStatuses)).isFalse();
        assertThat(bookingRepository.existsOverlappingActiveBooking(
                customer.getId(), PICKUP_DATE.minusDays(2), PICKUP_DATE, activeStatuses)).isFalse();
    }

    @Test
    void concurrentCreateOnlyOneCustomerWinsAvailabilityLock() throws Exception {
        saveAvailability(listing.getId(), PICKUP_DATE, RETURN_DATE, AvailabilityStatus.FREE);
        List<String> tokens = new ArrayList<>();
        int attemptCount = 8;
        for (int i = 0; i < attemptCount; i++) {
            AuthUser concurrentCustomer = saveUser("concurrent-" + i, Role.CUSTOMER);
            tokens.add(token(concurrentCustomer, Role.CUSTOMER));
        }

        CountDownLatch ready = new CountDownLatch(tokens.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tokens.size());
        List<Callable<BookingAttempt>> tasks = tokens.stream()
                .map(token -> (Callable<BookingAttempt>) () -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    MvcResult result = postCreate(token, uuidV4(), createBody(listing.getId(), PICKUP_DATE, RETURN_DATE))
                            .andReturn();
                    JsonNode body = json(result);
                    return new BookingAttempt(
                            result.getResponse().getStatus(),
                            body.has("id") ? UUID.fromString(body.get("id").asText()) : null,
                            body.has("code") ? body.get("code").asText() : null);
                })
                .toList();

        var futures = tasks.stream().map(executor::submit).toList();
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<BookingAttempt> attempts = new ArrayList<>();
        for (var future : futures) {
            attempts.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(attempts).filteredOn(attempt -> attempt.status() == 201).hasSize(1);
        assertThat(attempts)
                .filteredOn(attempt -> attempt.status() == 409 && "LISTING_NOT_AVAILABLE".equals(attempt.code()))
                .hasSize(attemptCount - 1);
        UUID winningBookingId = attempts.stream()
                .filter(attempt -> attempt.status() == 201)
                .findFirst()
                .orElseThrow()
                .bookingId();

        List<Booking> heldBookings = bookingRepository.findAll().stream()
                .filter(booking -> booking.getListingId().equals(listing.getId()))
                .filter(booking -> booking.getPickupDate().equals(PICKUP_DATE))
                .filter(booking -> booking.getReturnDate().equals(RETURN_DATE))
                .filter(booking -> booking.getStatus() == BookingStatus.HELD)
                .toList();
        assertThat(heldBookings).hasSize(1);
        assertThat(heldBookings.get(0).getId()).isEqualTo(winningBookingId);

        List<AvailabilityCalendar> rows = availabilityRepository.findByListingIdAndAvailableDateRange(
                listing.getId(), PICKUP_DATE, RETURN_DATE);
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.HOLD);
            assertThat(row.getBookingId()).isEqualTo(winningBookingId);
        });
    }

    private AuthUser saveUser(String prefix, Role... roles) {
        AuthUser user = new AuthUser(prefix + "-" + UUID.randomUUID() + "@example.com", "hash", UserStatus.ACTIVE, true);
        for (Role role : roles) {
            user.addRole(role);
        }
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

    private Booking saveBooking(
            AuthUser bookingCustomer,
            AuthUser bookingHost,
            Listing bookingListing,
            BookingStatus status,
            LocalDate pickupDate,
            LocalDate returnDate) {
        Booking booking = new Booking();
        booking.setCustomerId(bookingCustomer.getId());
        booking.setHostId(bookingHost.getId());
        booking.setListingId(bookingListing.getId());
        booking.setPickupDate(pickupDate);
        booking.setReturnDate(returnDate);
        booking.setPickupLocation("Hanoi");
        booking.setReturnLocation("Hanoi");
        booking.setStatus(status);
        booking.setHoldToken(UUID.randomUUID());
        booking.setHoldExpiresAt(Instant.now().plusSeconds(900));
        booking.setPriceSnapshot("""
                {"baseAmount":1400000.00,"extraAmount":0.00,"totalAmount":1400000.00,"currency":"VND","extras":[]}
                """);
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"FLEXIBLE","instantBook":true,"dailyKmLimit":200}
                """);
        return bookingRepository.save(booking);
    }

    private org.springframework.test.web.servlet.ResultActions postCreate(String token, String idempotencyKey, String body)
            throws Exception {
        return mockMvc.perform(post("/api/v1/bookings")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
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

    private JsonNode json(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        return content == null || content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }

    private String uuidV4() {
        return UUID.randomUUID().toString();
    }

    private record BookingAttempt(int status, UUID bookingId, String code) {
    }
}
