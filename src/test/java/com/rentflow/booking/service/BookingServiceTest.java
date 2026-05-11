package com.rentflow.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rentflow.auth.entity.Role;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingExtra;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingExtraRepository;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.AccessDeniedException;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.DriverLicenseNotApprovedException;
import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Extra;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.entity.PricingType;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.UserProfileRepository;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-11T00:00:00Z");
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID HOST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LISTING_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID VEHICLE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID EXTRA_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID IDEMPOTENCY_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID BOOKING_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";
    private static final String REQUEST_HASH = "request-hash";

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingExtraRepository bookingExtraRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private AvailabilityCalendarRepository availabilityRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private SecurityContext securityContext;

    private BookingService bookingService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        bookingService = new BookingService(
                bookingRepository,
                bookingExtraRepository,
                listingRepository,
                availabilityRepository,
                userProfileRepository,
                idempotencyService,
                new BookingPriceCalculator(),
                securityContext,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                false,
                15);
    }

    @Test
    void replayReturnsStoredResponseWithoutBusinessLogic() throws Exception {
        CreateBookingRequest request = request();
        BookingResponse replayed = response();
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(request)).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.CREATE_BOOKING, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.replay(201, objectMapper.writeValueAsString(replayed)));

        BookingResponse result = bookingService.createBooking(IDEMPOTENCY_KEY, request);

        assertThat(result).isEqualTo(replayed);
        verifyNoInteractions(listingRepository, bookingRepository, availabilityRepository, bookingExtraRepository);
    }

    @Test
    void createBookingSavesHeldBookingExtrasAvailabilityAndCompletesIdempotency() {
        CreateBookingRequest request = request();
        Listing listing = activeListing();
        Extra extra = extra(listing);
        listing.setExtras(List.of(extra));
        List<AvailabilityCalendar> rows = availabilityRows(AvailabilityStatus.FREE, AvailabilityStatus.FREE);
        mockProceed();
        when(listingRepository.findByIdAndStatusWithVehicleAndExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(listing));
        when(bookingRepository.existsOverlappingActiveBooking(eq(CUSTOMER_ID), any(), any(), any()))
                .thenReturn(false);
        when(availabilityRepository.findForBookingRangeForUpdate(
                LISTING_ID, request.pickupDate(), request.returnDate()))
                .thenReturn(rows);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(BOOKING_ID);
            return booking;
        });

        BookingResponse response = bookingService.createBooking(IDEMPOTENCY_KEY, request);

        assertThat(response.id()).isEqualTo(BOOKING_ID);
        assertThat(response.status()).isEqualTo(BookingStatus.HELD);
        assertThat(response.totalAmount()).isEqualByComparingTo("1500000.00");
        assertThat(response.holdExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(response.priceSnapshot().get("extras").get(0).get("lineAmount").decimalValue())
                .isEqualByComparingTo("100000.00");

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        Booking savedBooking = bookingCaptor.getValue();
        assertThat(savedBooking.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(savedBooking.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(savedBooking.getHostId()).isEqualTo(HOST_ID);
        assertThat(savedBooking.getHoldToken()).isNotNull();
        assertThat(savedBooking.getPolicySnapshot()).contains("\"cancellationPolicy\":\"FLEXIBLE\"");

        ArgumentCaptor<List<BookingExtra>> extrasCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookingExtraRepository).saveAll(extrasCaptor.capture());
        assertThat(extrasCaptor.getValue()).hasSize(1);
        assertThat(extrasCaptor.getValue().get(0).getPriceSnapshot()).isEqualByComparingTo("50000.00");

        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.HOLD);
            assertThat(row.getBookingId()).isEqualTo(BOOKING_ID);
            assertThat(row.getHoldToken()).isEqualTo(savedBooking.getHoldToken());
            assertThat(row.getHoldExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        });
        verify(availabilityRepository).saveAll(rows);
        verify(idempotencyService).complete(eq(IDEMPOTENCY_ID), eq(201), any());
    }

    @Test
    void nonCustomerThrowsAccessDenied() {
        mockProceed();
        doThrow(new AccessDeniedException()).when(securityContext).requireRole(Role.CUSTOMER);

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request()))
                .isInstanceOf(AccessDeniedException.class);
        verify(listingRepository, never()).findByIdAndStatusWithVehicleAndExtras(any(), any());
    }

    @Test
    void driverGateThrowsWhenProfileNotApproved() {
        bookingService = new BookingService(
                bookingRepository,
                bookingExtraRepository,
                listingRepository,
                availabilityRepository,
                userProfileRepository,
                idempotencyService,
                new BookingPriceCalculator(),
                securityContext,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                true,
                15);
        mockProceed();
        UserProfile profile = new UserProfile();
        profile.setDriverVerificationStatus(UserProfile.DriverVerificationStatus.PENDING);
        when(userProfileRepository.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request()))
                .isInstanceOf(DriverLicenseNotApprovedException.class)
                .hasFieldOrPropertyWithValue("code", "DRIVER_LICENSE_NOT_APPROVED");
    }

    @Test
    void invalidListingThrowsListingNotFound() {
        mockProceed();
        when(listingRepository.findByIdAndStatusWithVehicleAndExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request()))
                .isInstanceOf(ListingNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "LISTING_NOT_FOUND");
    }

    @Test
    void inactiveVehicleThrowsListingNotFound() {
        mockProceed();
        Listing listing = activeListing();
        listing.getVehicle().setStatus(VehicleStatus.SUSPENDED);
        when(listingRepository.findByIdAndStatusWithVehicleAndExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request()))
                .isInstanceOf(ListingNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "LISTING_NOT_FOUND");
    }

    @Test
    void selfBookingThrowsAccessDenied() {
        mockProceed();
        Listing listing = activeListing();
        listing.setHostId(CUSTOMER_ID);
        when(listingRepository.findByIdAndStatusWithVehicleAndExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void invalidDatesThrowValidationError() {
        CreateBookingRequest request = new CreateBookingRequest(
                LISTING_ID,
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 5, 11),
                "Hanoi",
                "Hanoi",
                List.of());
        mockProceed(request);
        when(listingRepository.findByIdAndStatusWithVehicleAndExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(activeListing()));

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void overlapThrowsBookingOverlapCustomer() {
        mockProceed();
        when(listingRepository.findByIdAndStatusWithVehicleAndExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(activeListing()));
        when(bookingRepository.existsOverlappingActiveBooking(eq(CUSTOMER_ID), any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_OVERLAP_CUSTOMER");
    }

    @Test
    void missingAvailabilityThrowsListingNotAvailable() {
        mockProceed();
        when(listingRepository.findByIdAndStatusWithVehicleAndExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(activeListing()));
        when(bookingRepository.existsOverlappingActiveBooking(eq(CUSTOMER_ID), any(), any(), any()))
                .thenReturn(false);
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any()))
                .thenReturn(List.of(new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 6, 1))));

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "LISTING_NOT_AVAILABLE");
    }

    @Test
    void nonFreeAvailabilityThrowsListingNotAvailable() {
        mockProceed();
        when(listingRepository.findByIdAndStatusWithVehicleAndExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(activeListing()));
        when(bookingRepository.existsOverlappingActiveBooking(eq(CUSTOMER_ID), any(), any(), any()))
                .thenReturn(false);
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any()))
                .thenReturn(availabilityRows(AvailabilityStatus.FREE, AvailabilityStatus.BLOCKED));

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "LISTING_NOT_AVAILABLE");
    }

    @Test
    void listMyBookingsReturnsCurrentCustomerBookings() {
        Booking booking = booking();
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(bookingRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(
                CUSTOMER_ID, BookingStatus.HELD, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(booking), PageRequest.of(0, 20), 1));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(activeListing()));

        var result = bookingService.listMyBookings(BookingStatus.HELD, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(BOOKING_ID);
        assertThat(result.content().get(0).listingTitle()).isEqualTo("Toyota Vios 2022");
        assertThat(result.content().get(0).totalAmount()).isEqualByComparingTo("1500000.00");
        verify(securityContext).requireRole(Role.CUSTOMER);
    }

    @Test
    void getBookingAllowsCustomerOwner() {
        Booking booking = booking();
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(activeListing()));

        BookingResponse result = bookingService.getBooking(BOOKING_ID);

        assertThat(result.id()).isEqualTo(BOOKING_ID);
        assertThat(result.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(result.totalAmount()).isEqualByComparingTo("1500000.00");
    }

    @Test
    void getBookingAllowsHostOwner() {
        Booking booking = booking();
        when(securityContext.currentUserId()).thenReturn(HOST_ID);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(activeListing()));

        BookingResponse result = bookingService.getBooking(BOOKING_ID);

        assertThat(result.hostId()).isEqualTo(HOST_ID);
    }

    @Test
    void getBookingAllowsAdmin() {
        Booking booking = booking();
        UUID adminId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        when(securityContext.currentUserId()).thenReturn(adminId);
        when(securityContext.hasRole(Role.ADMIN)).thenReturn(true);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(activeListing()));

        BookingResponse result = bookingService.getBooking(BOOKING_ID);

        assertThat(result.id()).isEqualTo(BOOKING_ID);
    }

    @Test
    void getBookingRejectsUnauthorizedAsNotFound() {
        Booking booking = booking();
        UUID otherUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(securityContext.currentUserId()).thenReturn(otherUserId);
        when(securityContext.hasRole(Role.ADMIN)).thenReturn(false);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.getBooking(BOOKING_ID))
                .isInstanceOf(com.rentflow.common.exception.BookingNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_NOT_FOUND");
    }

    @Test
    void patchBookingLocationsUpdatesOwnerBookingUsingLock() {
        Booking booking = booking();
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(activeListing()));

        BookingResponse result = bookingService.patchBookingLocations(
                BOOKING_ID,
                new PatchBookingLocationRequest("New pickup", "New return"));

        assertThat(result.pickupLocation()).isEqualTo("New pickup");
        assertThat(result.returnLocation()).isEqualTo("New return");
        assertThat(booking.getPickupLocation()).isEqualTo("New pickup");
        assertThat(booking.getReturnLocation()).isEqualTo("New return");
        verify(bookingRepository).findByIdForUpdate(BOOKING_ID);
    }

    @Test
    void patchBookingLocationsRejectsNonOwnerAsNotFound() {
        Booking booking = booking();
        UUID otherUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(securityContext.currentUserId()).thenReturn(otherUserId);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.patchBookingLocations(
                BOOKING_ID,
                new PatchBookingLocationRequest("New pickup", null)))
                .isInstanceOf(BookingNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_NOT_FOUND");
    }

    @Test
    void patchBookingLocationsRejectsInvalidStatus() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.CANCELLED);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.patchBookingLocations(
                BOOKING_ID,
                new PatchBookingLocationRequest("New pickup", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_INVALID_STATUS");
    }

    private void mockProceed() {
        mockProceed(request());
    }

    private void mockProceed(CreateBookingRequest request) {
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(request)).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.CREATE_BOOKING, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
    }

    private CreateBookingRequest request() {
        return new CreateBookingRequest(
                LISTING_ID,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                "Hanoi",
                "Hanoi",
                List.of(new RequestedExtra(EXTRA_ID, 1)));
    }

    private BookingResponse response() {
        return new BookingResponse(
                BOOKING_ID,
                BookingStatus.HELD,
                LISTING_ID,
                "Toyota Vios 2022",
                CUSTOMER_ID,
                HOST_ID,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                "Hanoi",
                "Hanoi",
                NOW.plusSeconds(900),
                new BigDecimal("1500000.00"),
                "VND",
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode(),
                NOW);
    }

    private Listing activeListing() {
        Listing listing = new Listing();
        listing.setId(LISTING_ID);
        listing.setVehicleId(VEHICLE_ID);
        listing.setHostId(HOST_ID);
        listing.setTitle("Toyota Vios 2022");
        listing.setBasePricePerDay(new BigDecimal("700000.00"));
        listing.setCurrency("VND");
        listing.setInstantBook(true);
        listing.setDailyKmLimit(200);
        listing.setCancellationPolicy(CancellationPolicy.FLEXIBLE);
        listing.setStatus(ListingStatus.ACTIVE);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(VEHICLE_ID);
        vehicle.setStatus(VehicleStatus.ACTIVE);
        listing.setVehicle(vehicle);
        listing.setExtras(List.of());
        return listing;
    }

    private Extra extra(Listing listing) {
        Extra extra = new Extra();
        extra.setId(EXTRA_ID);
        extra.setListing(listing);
        extra.setName("GPS");
        extra.setPricingType(PricingType.PER_DAY);
        extra.setPrice(new BigDecimal("50000.00"));
        extra.setActive(true);
        return extra;
    }

    private List<AvailabilityCalendar> availabilityRows(AvailabilityStatus first, AvailabilityStatus second) {
        AvailabilityCalendar dayOne = new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 6, 1));
        dayOne.setStatus(first);
        AvailabilityCalendar dayTwo = new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 6, 2));
        dayTwo.setStatus(second);
        return List.of(dayOne, dayTwo);
    }

    private Booking booking() {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setStatus(BookingStatus.HELD);
        booking.setListingId(LISTING_ID);
        booking.setCustomerId(CUSTOMER_ID);
        booking.setHostId(HOST_ID);
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setPickupLocation("Hanoi");
        booking.setReturnLocation("Hanoi");
        booking.setHoldExpiresAt(NOW.plusSeconds(900));
        booking.setPriceSnapshot("""
                {"totalAmount":1500000.00,"currency":"VND","extras":[]}
                """);
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"FLEXIBLE","instantBook":true,"dailyKmLimit":200}
                """);
        booking.setCreatedAt(NOW);
        return booking;
    }
}
