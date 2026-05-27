package com.rentflow.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rentflow.auth.entity.Role;
import com.rentflow.audit.service.AuditLogService;
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
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.PaymentProviderUnavailableException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.idempotency.service.IdempotencyResolution;
import com.rentflow.common.idempotency.service.IdempotencyFailureMarker;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import com.rentflow.common.idempotency.service.IdempotencyService;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.provider.CaptureResult;
import com.rentflow.payment.provider.PaymentProvider;
import com.rentflow.payment.provider.PaymentProviderRouter;
import com.rentflow.payment.provider.VoidCommand;
import com.rentflow.payment.provider.VoidResult;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Extra;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.entity.PricingType;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.UserProfileRepository;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
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
import static org.mockito.Mockito.lenient;
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
    @Mock private VehicleRepository vehicleRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private IdempotencyFailureMarker idempotencyFailureMarker;
    @Mock private SecurityContext securityContext;
    @Mock private BookingPaymentRepository bookingPaymentRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PaymentProviderRouter paymentProviderRouter;
    @Mock private PaymentProvider paymentProvider;
    @Mock private CorrelationIdHelper correlationIdHelper;
    @Mock private BookingTimelineService bookingTimelineService;
    @Mock private AuditLogService auditLogService;
    @Mock private OutboxService outboxService;

    private BookingService bookingService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        lenient().when(vehicleRepository.findById(VEHICLE_ID))
                .thenReturn(Optional.of(vehicle(VehicleStatus.ACTIVE)));
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        BookingValidator validator = new BookingValidator(
                listingRepository, bookingRepository, userProfileRepository, vehicleRepository, fixedClock, false);
        AvailabilityReserver reserver = new AvailabilityReserver(availabilityRepository);
        bookingService = new BookingService(
                bookingRepository,
                bookingExtraRepository,
                listingRepository,
                idempotencyService,
                idempotencyFailureMarker,
                new BookingPriceCalculator(),
                validator,
                reserver,
                securityContext,
                objectMapper,
                new com.rentflow.booking.mapper.BookingMapper(listingRepository, objectMapper),
                bookingPaymentRepository,
                paymentTransactionRepository,
                paymentProviderRouter,
                correlationIdHelper,
                new CancellationPolicyCalculator(fixedClock),
                bookingTimelineService,
                auditLogService,
                outboxService,
                fixedClock,
                15);
        lenient().when(correlationIdHelper.getOrGenerate()).thenReturn("corr-1");
        lenient().when(paymentProviderRouter.route(PaymentProviderType.COREBANK)).thenReturn(paymentProvider);
        lenient().when(paymentTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
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
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
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
        verify(idempotencyFailureMarker, never()).markFailed(any());
    }

    @Test
    void createBookingBusinessFailureMarksIdempotencyFailed() {
        mockProceed();
        doThrow(new AccessDeniedException()).when(securityContext).requireRole(Role.CUSTOMER);

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request()))
                .isInstanceOf(AccessDeniedException.class);
        verify(listingRepository, never()).findByIdAndStatusWithExtras(any(), any());
        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_ID);
    }

    @Test
    void driverGateThrowsWhenProfileNotApproved() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        BookingValidator validator = new BookingValidator(
                listingRepository, bookingRepository, userProfileRepository, vehicleRepository, fixedClock, true);
        AvailabilityReserver reserver = new AvailabilityReserver(availabilityRepository);
        bookingService = new BookingService(
                bookingRepository,
                bookingExtraRepository,
                listingRepository,
                idempotencyService,
                idempotencyFailureMarker,
                new BookingPriceCalculator(),
                validator,
                reserver,
                securityContext,
                objectMapper,
                new com.rentflow.booking.mapper.BookingMapper(listingRepository, objectMapper),
                bookingPaymentRepository,
                paymentTransactionRepository,
                paymentProviderRouter,
                correlationIdHelper,
                new CancellationPolicyCalculator(fixedClock),
                bookingTimelineService,
                auditLogService,
                outboxService,
                fixedClock,
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
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request()))
                .isInstanceOf(ListingNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "LISTING_NOT_FOUND");
    }

    @Test
    void inactiveVehicleThrowsListingNotFound() {
        mockProceed();
        Listing listing = activeListing();
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle(VehicleStatus.SUSPENDED)));
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
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
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
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
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(activeListing()));

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void returnDateBeforePickupDateThrowsValidationError() {
        CreateBookingRequest request = new CreateBookingRequest(
                LISTING_ID,
                LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 1),
                "Hanoi",
                "Hanoi",
                List.of());
        mockProceed(request);
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(activeListing()));

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void returnDateEqualPickupDateThrowsValidationError() {
        CreateBookingRequest request = new CreateBookingRequest(
                LISTING_ID,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 1),
                "Hanoi",
                "Hanoi",
                List.of());
        mockProceed(request);
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(activeListing()));

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void oneDayBookingIsValid() {
        CreateBookingRequest request = new CreateBookingRequest(
                LISTING_ID,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2),
                "Hanoi",
                "Hanoi",
                List.of());
        mockValidCreate(request, availabilityRows(AvailabilityStatus.FREE));

        BookingResponse response = bookingService.createBooking(IDEMPOTENCY_KEY, request);

        assertThat(response.status()).isEqualTo(BookingStatus.HELD);
        assertThat(response.priceSnapshot().get("rentalDays").asLong()).isEqualTo(1);
    }

    @Test
    void thirtyDayBookingIsValid() {
        CreateBookingRequest request = new CreateBookingRequest(
                LISTING_ID,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1),
                "Hanoi",
                "Hanoi",
                List.of());
        List<AvailabilityCalendar> rows = java.util.stream.IntStream.range(0, 30)
                .mapToObj(day -> {
                    AvailabilityCalendar row = new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 6, 1).plusDays(day));
                    row.setStatus(AvailabilityStatus.FREE);
                    return row;
                })
                .toList();
        mockValidCreate(request, rows);

        BookingResponse response = bookingService.createBooking(IDEMPOTENCY_KEY, request);

        assertThat(response.status()).isEqualTo(BookingStatus.HELD);
        assertThat(response.priceSnapshot().get("rentalDays").asLong()).isEqualTo(30);
    }

    @Test
    void moreThanThirtyDaysThrowsValidationError() {
        CreateBookingRequest request = new CreateBookingRequest(
                LISTING_ID,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 2),
                "Hanoi",
                "Hanoi",
                List.of());
        mockProceed(request);
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(activeListing()));

        assertThatThrownBy(() -> bookingService.createBooking(IDEMPOTENCY_KEY, request))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void overlapThrowsBookingOverlapCustomer() {
        mockProceed();
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
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
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
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
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
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

    @Test
    void cancelBookingCancelsHeldBookingReleasesAvailabilityAndCompletesIdempotency() {
        Booking booking = booking();
        UUID holdToken = UUID.fromString("12121212-1212-4212-8212-121212121212");
        booking.setHoldToken(holdToken);
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking, holdToken);
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.CANCEL_BOOKING, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(availabilityRepository.findForBookingRangeForUpdate(
                LISTING_ID, booking.getPickupDate(), booking.getReturnDate()))
                .thenReturn(rows);

        CancelBookingResponse response = bookingService.cancelBooking(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                new CancelBookingRequest(" <b>Change</b> of <script>x</script> plan "));

        assertThat(response.id()).isEqualTo(BOOKING_ID);
        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo("Change of x plan");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).isEqualTo("Change of x plan");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.FREE);
            assertThat(row.getBookingId()).isNull();
            assertThat(row.getHoldToken()).isNull();
            assertThat(row.getHoldExpiresAt()).isNull();
        });
        verify(bookingRepository).findByIdForUpdate(BOOKING_ID);
        verify(bookingRepository).save(booking);
        verify(availabilityRepository).saveAll(rows);
        verify(idempotencyService).complete(eq(IDEMPOTENCY_ID), eq(200), any());
    }

    @Test
    void cancelBookingReplayReturnsStoredResponseWithoutBusinessLogic() throws Exception {
        CancelBookingRequest request = new CancelBookingRequest("Change of plan");
        CancelBookingResponse replayed = new CancelBookingResponse(BOOKING_ID, BookingStatus.CANCELLED, "Change of plan");
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.CANCEL_BOOKING, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.replay(200, objectMapper.writeValueAsString(replayed)));

        CancelBookingResponse result = bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, request);

        assertThat(result).isEqualTo(replayed);
        verify(bookingRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(availabilityRepository);
    }

    @Test
    void cancelBookingConfirmedStatusThrowsBookingInvalidStatus() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.IN_PROGRESS);
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                new CancelBookingRequest("Change of plan")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_INVALID_STATUS");
        verify(idempotencyFailureMarker).markFailed(IDEMPOTENCY_ID);
    }

    @Test
    void cancelBookingPendingHostApprovalVoidsPaymentAndCancels() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.PENDING_HOST_APPROVAL);
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking, UUID.randomUUID());
        BookingPayment payment = authorizedPayment();
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(rows);
        when(paymentProvider.voidAuthorization(any())).thenReturn(new VoidResult("VOIDED", "{\"status\":\"VOIDED\"}"));

        CancelBookingResponse response = bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("Host declined"));

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(response.voidRetryRequired()).isFalse();
    }

    @Test
    void cancelBookingConfirmedPartialPenaltyReturnsRetryRequiredWhenVoidFails() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"MODERATE","instantBook":true,"dailyKmLimit":200}
                """);
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking, UUID.randomUUID());
        BookingPayment payment = authorizedPayment();
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(rows);
        when(paymentProvider.capture(any())).thenReturn(new CaptureResult("CAPTURED", "journal-1", "{\"status\":\"CAPTURED\"}"));
        doThrow(new PaymentProviderUnavailableException("void failed")).when(paymentProvider).voidAuthorization(any(VoidCommand.class));

        CancelBookingResponse response = bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("Late cancel"));

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.voidRetryRequired()).isTrue();
        assertThat(response.code()).isEqualTo("PAYMENT_VOID_RETRY_REQUIRED");
    }

    @Test
    void cancelBookingConfirmedFullRefundVoidsAndCancels() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.CONFIRMED);
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking, UUID.randomUUID());
        BookingPayment payment = authorizedPayment();
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(rows);
        when(paymentProvider.voidAuthorization(any())).thenReturn(new VoidResult("VOIDED", "{\"status\":\"VOIDED\"}"));

        CancelBookingResponse response = bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("Full refund cancel"));

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.voidRetryRequired()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
        verify(paymentProvider).voidAuthorization(any());
        verify(paymentProvider, never()).capture(any());
    }

    @Test
    void cancelBookingConfirmedPartialPenaltyCaptureThenVoidSucceeds() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"MODERATE","instantBook":true,"dailyKmLimit":200}
                """);
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking, UUID.randomUUID());
        BookingPayment payment = authorizedPayment();
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(rows);
        when(paymentProvider.capture(any())).thenReturn(new CaptureResult("CAPTURED", "journal-1", "{\"status\":\"CAPTURED\"}"));
        when(paymentProvider.voidAuthorization(any())).thenReturn(new VoidResult("VOIDED", "{\"status\":\"VOIDED\"}"));

        CancelBookingResponse response = bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("Late cancel"));

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.voidRetryRequired()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getAuthorizedAmount()).isEqualByComparingTo("1500000.00");
        assertThat(payment.getCapturedAmount()).isEqualByComparingTo("750000.00");
        assertThat(payment.getProviderStatus()).isEqualTo("VOIDED");
        verify(paymentProvider).capture(any());
        verify(paymentProvider).voidAuthorization(any());
    }

    @Test
    void cancelBookingConfirmedRejectsNonCoreBankProvider() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.CONFIRMED);
        BookingPayment payment = authorizedPayment();
        payment.setProvider(PaymentProviderType.VIETQR_MANUAL);
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_PROVIDER_UNSUPPORTED");
    }

    @Test
    void cancelBookingConfirmedRejectsNonAuthorizedPayment() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.CONFIRMED);
        BookingPayment payment = authorizedPayment();
        payment.setStatus(PaymentStatus.CAPTURED);
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_INVALID_STATUS");
    }

    @Test
    void cancelBookingHostAllowed() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.HELD);
        UUID hostUserId = HOST_ID;
        when(securityContext.currentUserId()).thenReturn(hostUserId);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(eq(hostUserId), eq(IdempotencyScope.CANCEL_BOOKING), eq(IDEMPOTENCY_KEY), eq(REQUEST_HASH)))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(List.of());

        CancelBookingResponse response = bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("Host cancel"));

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBookingAdminAllowed() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.HELD);
        UUID adminId = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
        when(securityContext.currentUserId()).thenReturn(adminId);
        when(securityContext.hasRole(Role.ADMIN)).thenReturn(true);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(eq(adminId), eq(IdempotencyScope.CANCEL_BOOKING), eq(IDEMPOTENCY_KEY), eq(REQUEST_HASH)))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(List.of());

        CancelBookingResponse response = bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("Admin cancel"));

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBookingConfirmedPaymentMissingThrows() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.CONFIRMED);
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.empty());
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_NOT_FOUND");
    }

    @Test
    void cancelBookingReplayPreservesVoidRetryRequiredFlag() throws Exception {
        CancelBookingResponse replayed = new CancelBookingResponse(
                BOOKING_ID,
                BookingStatus.CANCELLED,
                "Late cancel",
                true,
                true,
                "PAYMENT_VOID_RETRY_REQUIRED",
                "VOID_RETRY_REQUIRED");
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.CANCEL_BOOKING, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.replay(202, objectMapper.writeValueAsString(replayed)));

        CancelBookingResponse result = bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("Late cancel"));

        assertThat(result.voidRetryRequired()).isTrue();
        assertThat(result.code()).isEqualTo("PAYMENT_VOID_RETRY_REQUIRED");
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        verifyNoInteractions(paymentProvider);
    }

    @Test
    void cancelBookingReplayDoesNotReinvokePaymentMutations() throws Exception {
        CancelBookingResponse replayed = new CancelBookingResponse(
                BOOKING_ID,
                BookingStatus.CANCELLED,
                "Late cancel",
                true,
                true,
                "PAYMENT_VOID_RETRY_REQUIRED",
                "VOID_RETRY_REQUIRED");
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.CANCEL_BOOKING, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.replay(202, objectMapper.writeValueAsString(replayed)));

        bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("Late cancel"));

        verifyNoInteractions(paymentProvider);
        verify(bookingRepository, never()).findByIdForUpdate(any());
        verify(bookingPaymentRepository, never()).findByBookingIdForUpdate(any());
    }

    @Test
    void cancelBookingPaymentMissingThrowsOnPendingHostApproval() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.PENDING_HOST_APPROVAL);
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.empty());
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_NOT_FOUND");
    }

    @Test
    void cancelBookingConfirmedAfterPartialPenaltyStoresVoidFailureState() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"MODERATE","instantBook":true,"dailyKmLimit":200}
                """);
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking, UUID.randomUUID());
        BookingPayment payment = authorizedPayment();
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingPaymentRepository.findByBookingIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(rows);
        when(paymentProvider.capture(any())).thenReturn(new CaptureResult("CAPTURED", "journal-1", "{\"status\":\"CAPTURED\"}"));
        doThrow(new PaymentProviderUnavailableException("void fail")).when(paymentProvider).voidAuthorization(any());

        CancelBookingResponse response = bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("Late cancel"));

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.voidRetryRequired()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getProviderStatus()).isEqualTo("VOID_RETRY_REQUIRED");
    }

    private void mockCancelProceed() {
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.CANCEL_BOOKING, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
    }

    @Test
    void cancelBookingHashInputIncludesBookingIdAndRequest() {
        Booking booking = booking();
        when(securityContext.currentUserId()).thenReturn(CUSTOMER_ID);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(CUSTOMER_ID, IdempotencyScope.CANCEL_BOOKING, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(List.of());

        bookingService.cancelBooking(BOOKING_ID, IDEMPOTENCY_KEY, new CancelBookingRequest("Change of plan"));

        ArgumentCaptor<Object> hashCaptor = ArgumentCaptor.forClass(Object.class);
        verify(idempotencyService).computeHash(hashCaptor.capture());
        assertThat(hashCaptor.getValue())
                .usingRecursiveComparison()
                .isEqualTo(new Object() {
                    @SuppressWarnings("unused")
                    public final UUID bookingId = BOOKING_ID;
                    @SuppressWarnings("unused")
                    public final CancelBookingRequest request = new CancelBookingRequest("Change of plan");
                });
    }

    @Test
    void cancelBookingSanitizesBlankReasonToNull() {
        Booking booking = booking();
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(List.of());

        CancelBookingResponse response = bookingService.cancelBooking(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                new CancelBookingRequest(" <b> </b> "));

        assertThat(response.cancellationReason()).isNull();
        assertThat(booking.getCancellationReason()).isNull();
    }

    @Test
    void cancelBookingTruncatesReasonToFiveHundredCharacters() {
        Booking booking = booking();
        mockCancelProceed();
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(List.of());

        CancelBookingResponse response = bookingService.cancelBooking(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                new CancelBookingRequest("a".repeat(550)));

        assertThat(response.cancellationReason()).hasSize(500);
        assertThat(booking.getCancellationReason()).hasSize(500);
    }

    @Test
    void cancelBookingNonOwnerThrowsBookingNotFound() {
        Booking booking = booking();
        UUID otherUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(securityContext.currentUserId()).thenReturn(otherUserId);
        when(idempotencyService.computeHash(any())).thenReturn(REQUEST_HASH);
        when(idempotencyService.resolve(otherUserId, IdempotencyScope.CANCEL_BOOKING, IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyResolution.proceed(IDEMPOTENCY_ID));
        when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(
                BOOKING_ID,
                IDEMPOTENCY_KEY,
                new CancelBookingRequest("Change of plan")))
                .isInstanceOf(BookingNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_NOT_FOUND");
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

    private void mockValidCreate(CreateBookingRequest request, List<AvailabilityCalendar> availabilityRows) {
        mockProceed(request);
        Listing listing = activeListing();
        listing.setExtras(List.of());
        when(listingRepository.findByIdAndStatusWithExtras(LISTING_ID, ListingStatus.ACTIVE))
                .thenReturn(Optional.of(listing));
        when(bookingRepository.existsOverlappingActiveBooking(eq(CUSTOMER_ID), any(), any(), any()))
                .thenReturn(false);
        when(availabilityRepository.findForBookingRangeForUpdate(
                LISTING_ID, request.pickupDate(), request.returnDate()))
                .thenReturn(availabilityRows);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(BOOKING_ID);
            return booking;
        });
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
        listing.setExtras(List.of());
        return listing;
    }

    private Vehicle vehicle(VehicleStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(VEHICLE_ID);
        vehicle.setStatus(status);
        return vehicle;
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

    private List<AvailabilityCalendar> availabilityRows(AvailabilityStatus first) {
        AvailabilityCalendar dayOne = new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 6, 1));
        dayOne.setStatus(first);
        return List.of(dayOne);
    }

    private List<AvailabilityCalendar> heldAvailabilityRows(Booking booking, UUID holdToken) {
        AvailabilityCalendar dayOne = new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 6, 1));
        dayOne.setStatus(AvailabilityStatus.HOLD);
        dayOne.setBookingId(booking.getId());
        dayOne.setHoldToken(holdToken);
        dayOne.setHoldExpiresAt(booking.getHoldExpiresAt());
        AvailabilityCalendar dayTwo = new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 6, 2));
        dayTwo.setStatus(AvailabilityStatus.HOLD);
        dayTwo.setBookingId(booking.getId());
        dayTwo.setHoldToken(holdToken);
        dayTwo.setHoldExpiresAt(booking.getHoldExpiresAt());
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

    private BookingPayment authorizedPayment() {
        BookingPayment payment = new BookingPayment();
        payment.setId(UUID.fromString("88888888-8888-4888-8888-888888888888"));
        payment.setBookingId(BOOKING_ID);
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setCurrency("VND");
        payment.setAuthorizedAmount(new BigDecimal("1500000.00"));
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setProviderPaymentOrderId("payment-order-1");
        payment.setProviderHoldId("hold-1");
        return payment;
    }
}
