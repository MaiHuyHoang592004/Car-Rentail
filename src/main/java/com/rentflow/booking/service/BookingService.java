package com.rentflow.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.rentflow.listing.entity.Extra;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.UserProfileRepository;
import com.rentflow.vehicle.entity.VehicleStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.rentflow.common.web.PageResponse;

@Service
public class BookingService {

    private static final List<BookingStatus> CUSTOMER_OVERLAP_STATUSES = List.of(
            BookingStatus.HELD,
            BookingStatus.PENDING_HOST_APPROVAL,
            BookingStatus.CONFIRMED);
    private static final List<BookingStatus> PATCHABLE_STATUSES = List.of(
            BookingStatus.HELD,
            BookingStatus.PENDING_HOST_APPROVAL,
            BookingStatus.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final BookingExtraRepository bookingExtraRepository;
    private final ListingRepository listingRepository;
    private final AvailabilityCalendarRepository availabilityCalendarRepository;
    private final UserProfileRepository userProfileRepository;
    private final IdempotencyService idempotencyService;
    private final BookingPriceCalculator bookingPriceCalculator;
    private final SecurityContext securityContext;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean requireDriverVerification;
    private final long holdDurationMinutes;

    public BookingService(
            BookingRepository bookingRepository,
            BookingExtraRepository bookingExtraRepository,
            ListingRepository listingRepository,
            AvailabilityCalendarRepository availabilityCalendarRepository,
            UserProfileRepository userProfileRepository,
            IdempotencyService idempotencyService,
            BookingPriceCalculator bookingPriceCalculator,
            SecurityContext securityContext,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${rentflow.booking.require-driver-verification:false}") boolean requireDriverVerification,
            @Value("${rentflow.booking.hold-duration-minutes:15}") long holdDurationMinutes) {
        this.bookingRepository = bookingRepository;
        this.bookingExtraRepository = bookingExtraRepository;
        this.listingRepository = listingRepository;
        this.availabilityCalendarRepository = availabilityCalendarRepository;
        this.userProfileRepository = userProfileRepository;
        this.idempotencyService = idempotencyService;
        this.bookingPriceCalculator = bookingPriceCalculator;
        this.securityContext = securityContext;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.requireDriverVerification = requireDriverVerification;
        this.holdDurationMinutes = holdDurationMinutes;
    }

    @Transactional
    public BookingResponse createBooking(String idempotencyKey, CreateBookingRequest request) {
        UUID customerId = securityContext.currentUserId();
        String requestHash = idempotencyService.computeHash(request);
        IdempotencyResolution resolution = idempotencyService.resolve(
                customerId,
                IdempotencyScope.CREATE_BOOKING,
                idempotencyKey,
                requestHash);

        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeResponse(replay.responseBodyJson());
        }

        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            BookingResponse response = createBookingAfterIdempotency(customerId, request);
            idempotencyService.complete(idempotencyKeyId, 201, serialize(response));
            return response;
        } catch (RuntimeException e) {
            // TODO: Consider a REQUIRES_NEW failure marker in a later idempotency hardening step.
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> listMyBookings(BookingStatus status, Pageable pageable) {
        UUID customerId = securityContext.currentUserId();
        securityContext.requireRole(Role.CUSTOMER);

        Page<Booking> bookings = status == null
                ? bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                : bookingRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, status, pageable);

        return new PageResponse<>(
                bookings.getContent().stream().map(this::toSummaryResponse).toList(),
                bookings.getNumber(),
                bookings.getSize(),
                bookings.getTotalElements(),
                bookings.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(UUID id) {
        UUID currentUserId = securityContext.currentUserId();
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(id)));

        if (!canViewBooking(booking, currentUserId)) {
            throw new BookingNotFoundException(String.valueOf(id));
        }

        return toBookingResponse(booking);
    }

    @Transactional
    public BookingResponse patchBookingLocations(UUID id, PatchBookingLocationRequest request) {
        UUID currentUserId = securityContext.currentUserId();
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(id)));

        if (!booking.getCustomerId().equals(currentUserId)) {
            throw new BookingNotFoundException(String.valueOf(id));
        }
        if (!PATCHABLE_STATUSES.contains(booking.getStatus())) {
            throw new BusinessRuleException(
                    "BOOKING_INVALID_STATUS",
                    "Booking cannot be patched in its current status");
        }

        if (request.pickupLocation() != null) {
            booking.setPickupLocation(request.pickupLocation());
        }
        if (request.returnLocation() != null) {
            booking.setReturnLocation(request.returnLocation());
        }

        return toBookingResponse(booking);
    }

    @Transactional
    public CancelBookingResponse cancelBooking(UUID id, String idempotencyKey, CancelBookingRequest request) {
        CancelBookingRequest cancelRequest = request == null ? new CancelBookingRequest(null) : request;
        UUID customerId = securityContext.currentUserId();
        String requestHash = idempotencyService.computeHash(new CancelHashInput(id, cancelRequest));
        IdempotencyResolution resolution = idempotencyService.resolve(
                customerId,
                IdempotencyScope.CANCEL_BOOKING,
                idempotencyKey,
                requestHash);

        if (resolution instanceof IdempotencyResolution.Replay replay) {
            return deserializeCancelResponse(replay.responseBodyJson());
        }

        UUID idempotencyKeyId = ((IdempotencyResolution.Proceed) resolution).idempotencyKeyId();
        try {
            Booking booking = bookingRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new BookingNotFoundException(String.valueOf(id)));
            if (!booking.getCustomerId().equals(customerId)) {
                throw new BookingNotFoundException(String.valueOf(id));
            }
            if (booking.getStatus() != BookingStatus.HELD) {
                throw new BusinessRuleException(
                        "BOOKING_INVALID_STATUS",
                        "Booking cannot be cancelled in its current status");
            }

            List<AvailabilityCalendar> availabilityRows = availabilityCalendarRepository.findForBookingRangeForUpdate(
                    booking.getListingId(),
                    booking.getPickupDate(),
                    booking.getReturnDate());
            String cancellationReason = sanitizeCancellationReason(cancelRequest.reason());
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancellationReason(cancellationReason);
            bookingRepository.save(booking);

            List<AvailabilityCalendar> releasedRows = releaseHeldAvailability(availabilityRows, booking);
            if (!releasedRows.isEmpty()) {
                availabilityCalendarRepository.saveAll(releasedRows);
            }

            CancelBookingResponse response = new CancelBookingResponse(
                    booking.getId(),
                    booking.getStatus(),
                    booking.getCancellationReason());
            idempotencyService.complete(idempotencyKeyId, 200, serialize(response));
            return response;
        } catch (RuntimeException e) {
            // TODO: Consider a REQUIRES_NEW failure marker in a later idempotency hardening step.
            throw e;
        }
    }

    private BookingResponse createBookingAfterIdempotency(UUID customerId, CreateBookingRequest request) {
        securityContext.requireRole(Role.CUSTOMER);
        validateDriverVerification(customerId);

        Listing listing = listingRepository
                .findByIdAndStatusWithVehicleAndExtras(request.listingId(), ListingStatus.ACTIVE)
                .filter(this::vehicleIsActive)
                .orElseThrow(() -> new ListingNotFoundException(String.valueOf(request.listingId())));

        if (listing.getHostId().equals(customerId)) {
            throw new AccessDeniedException();
        }

        long rentalDays = validateDates(request.pickupDate(), request.returnDate());
        validateCustomerOverlap(customerId, request.pickupDate(), request.returnDate());
        List<AvailabilityCalendar> lockedAvailability = lockAndValidateAvailability(
                request.listingId(),
                request.pickupDate(),
                request.returnDate(),
                rentalDays);

        PriceCalculationResult price = bookingPriceCalculator.calculate(
                listing,
                request.pickupDate(),
                request.returnDate(),
                request.extras(),
                listing.getExtras());
        PolicySnapshot policySnapshot = new PolicySnapshot(
                listing.getCancellationPolicy(),
                listing.getInstantBook(),
                listing.getDailyKmLimit());
        String priceSnapshotJson = serialize(price);
        String policySnapshotJson = serialize(policySnapshot);

        Instant now = Instant.now(clock);
        UUID holdToken = UUID.randomUUID();
        Instant holdExpiresAt = now.plus(holdDurationMinutes, ChronoUnit.MINUTES);
        Booking booking = saveBooking(customerId, listing, request, holdToken, holdExpiresAt,
                priceSnapshotJson, policySnapshotJson);

        saveBookingExtras(booking.getId(), price.extras());
        holdAvailability(lockedAvailability, booking.getId(), holdToken, holdExpiresAt);

        JsonNode priceSnapshotNode = readTree(priceSnapshotJson);
        JsonNode policySnapshotNode = readTree(policySnapshotJson);
        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getListingId(),
                listing.getTitle(),
                booking.getCustomerId(),
                booking.getHostId(),
                booking.getPickupDate(),
                booking.getReturnDate(),
                booking.getPickupLocation(),
                booking.getReturnLocation(),
                booking.getHoldExpiresAt(),
                price.totalAmount(),
                price.currency(),
                priceSnapshotNode,
                policySnapshotNode,
                booking.getCreatedAt() == null ? now : booking.getCreatedAt());
    }

    private void validateDriverVerification(UUID customerId) {
        if (!requireDriverVerification) {
            return;
        }
        UserProfile profile = userProfileRepository.findByUserId(customerId)
                .orElseThrow(DriverLicenseNotApprovedException::new);
        if (profile.getDriverVerificationStatus() != UserProfile.DriverVerificationStatus.APPROVED) {
            throw new DriverLicenseNotApprovedException();
        }
    }

    private boolean canViewBooking(Booking booking, UUID currentUserId) {
        return booking.getCustomerId().equals(currentUserId)
                || booking.getHostId().equals(currentUserId)
                || securityContext.hasRole(Role.ADMIN);
    }

    private boolean vehicleIsActive(Listing listing) {
        return listing.getVehicle() != null && listing.getVehicle().getStatus() == VehicleStatus.ACTIVE;
    }

    private long validateDates(LocalDate pickupDate, LocalDate returnDate) {
        if (pickupDate == null || returnDate == null) {
            throw new ValidationException("pickupDate and returnDate are required");
        }
        if (pickupDate.isBefore(LocalDate.now(clock))) {
            throw new ValidationException("pickupDate must not be in the past");
        }
        if (!pickupDate.isBefore(returnDate)) {
            throw new ValidationException("pickupDate must be before returnDate");
        }
        long rentalDays = ChronoUnit.DAYS.between(pickupDate, returnDate);
        if (rentalDays < 1 || rentalDays > 30) {
            throw new ValidationException("rentalDays must be between 1 and 30");
        }
        return rentalDays;
    }

    private void validateCustomerOverlap(UUID customerId, LocalDate pickupDate, LocalDate returnDate) {
        boolean overlaps = bookingRepository.existsOverlappingActiveBooking(
                customerId,
                pickupDate,
                returnDate,
                CUSTOMER_OVERLAP_STATUSES);
        if (overlaps) {
            throw new BusinessRuleException(
                    "BOOKING_OVERLAP_CUSTOMER",
                    "Customer already has an active overlapping booking");
        }
    }

    private List<AvailabilityCalendar> lockAndValidateAvailability(
            UUID listingId,
            LocalDate pickupDate,
            LocalDate returnDate,
            long rentalDays) {
        List<AvailabilityCalendar> rows = availabilityCalendarRepository.findForBookingRangeForUpdate(
                listingId,
                pickupDate,
                returnDate);
        if (rows.size() != rentalDays || rows.stream().anyMatch(row -> row.getStatus() != AvailabilityStatus.FREE)) {
            throw new BusinessRuleException("LISTING_NOT_AVAILABLE", "Listing is not available for selected dates");
        }
        return rows;
    }

    private Booking saveBooking(
            UUID customerId,
            Listing listing,
            CreateBookingRequest request,
            UUID holdToken,
            Instant holdExpiresAt,
            String priceSnapshotJson,
            String policySnapshotJson) {
        Booking booking = new Booking();
        booking.setCustomerId(customerId);
        booking.setHostId(listing.getHostId());
        booking.setListingId(listing.getId());
        booking.setPickupDate(request.pickupDate());
        booking.setReturnDate(request.returnDate());
        booking.setStatus(BookingStatus.HELD);
        booking.setHoldToken(holdToken);
        booking.setHoldExpiresAt(holdExpiresAt);
        booking.setHostApprovalExpiresAt(null);
        booking.setPickupLocation(request.pickupLocation());
        booking.setReturnLocation(request.returnLocation());
        booking.setPriceSnapshot(priceSnapshotJson);
        booking.setPolicySnapshot(policySnapshotJson);
        return bookingRepository.save(booking);
    }

    private void saveBookingExtras(UUID bookingId, List<ExtraLineItem> lineItems) {
        List<BookingExtra> bookingExtras = lineItems.stream()
                .map(line -> {
                    BookingExtra bookingExtra = new BookingExtra();
                    bookingExtra.setBookingId(bookingId);
                    bookingExtra.setExtraId(line.extraId());
                    bookingExtra.setQuantity(line.quantity());
                    bookingExtra.setPriceSnapshot(line.unitPrice());
                    return bookingExtra;
                })
                .toList();
        if (!bookingExtras.isEmpty()) {
            bookingExtraRepository.saveAll(bookingExtras);
        }
    }

    private void holdAvailability(
            List<AvailabilityCalendar> rows,
            UUID bookingId,
            UUID holdToken,
            Instant holdExpiresAt) {
        rows.forEach(row -> {
            row.setStatus(AvailabilityStatus.HOLD);
            row.setBookingId(bookingId);
            row.setHoldToken(holdToken);
            row.setHoldExpiresAt(holdExpiresAt);
        });
        availabilityCalendarRepository.saveAll(rows);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize booking JSON", e);
        }
    }

    private BookingResponse deserializeResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, BookingResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize idempotency response", e);
        }
    }

    private CancelBookingResponse deserializeCancelResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, CancelBookingResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize idempotency response", e);
        }
    }

    private String sanitizeCancellationReason(String reason) {
        if (reason == null) {
            return null;
        }
        String sanitized = reason.replaceAll("<[^>]*>", "").trim();
        if (sanitized.isBlank()) {
            return null;
        }
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private List<AvailabilityCalendar> releaseHeldAvailability(List<AvailabilityCalendar> rows, Booking booking) {
        return rows.stream()
                .filter(row -> row.getStatus() == AvailabilityStatus.HOLD)
                .filter(row -> booking.getId().equals(row.getBookingId()))
                .filter(row -> booking.getHoldToken() == null || booking.getHoldToken().equals(row.getHoldToken()))
                .peek(row -> {
                    row.setStatus(AvailabilityStatus.FREE);
                    row.setBookingId(null);
                    row.setHoldToken(null);
                    row.setHoldExpiresAt(null);
                })
                .toList();
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to read booking JSON", e);
        }
    }

    private BookingSummaryResponse toSummaryResponse(Booking booking) {
        JsonNode priceSnapshot = readTree(booking.getPriceSnapshot());
        return new BookingSummaryResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getListingId(),
                findListingTitle(booking.getListingId()),
                booking.getPickupDate(),
                booking.getReturnDate(),
                booking.getHoldExpiresAt(),
                amountFromSnapshot(priceSnapshot, "totalAmount"),
                textFromSnapshot(priceSnapshot, "currency"),
                booking.getCreatedAt());
    }

    private BookingResponse toBookingResponse(Booking booking) {
        JsonNode priceSnapshot = readTree(booking.getPriceSnapshot());
        JsonNode policySnapshot = readTree(booking.getPolicySnapshot());
        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getListingId(),
                findListingTitle(booking.getListingId()),
                booking.getCustomerId(),
                booking.getHostId(),
                booking.getPickupDate(),
                booking.getReturnDate(),
                booking.getPickupLocation(),
                booking.getReturnLocation(),
                booking.getHoldExpiresAt(),
                amountFromSnapshot(priceSnapshot, "totalAmount"),
                textFromSnapshot(priceSnapshot, "currency"),
                priceSnapshot,
                policySnapshot,
                booking.getCreatedAt());
    }

    private String findListingTitle(UUID listingId) {
        Optional<Listing> listing = listingRepository.findById(listingId);
        return listing.map(Listing::getTitle).orElse(null);
    }

    private BigDecimal amountFromSnapshot(JsonNode snapshot, String field) {
        JsonNode value = snapshot.get(field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private String textFromSnapshot(JsonNode snapshot, String field) {
        JsonNode value = snapshot.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private record CancelHashInput(UUID bookingId, CancelBookingRequest request) {
    }
}
