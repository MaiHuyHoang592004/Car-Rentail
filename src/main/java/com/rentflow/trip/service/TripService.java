package com.rentflow.trip.service;

import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.payment.service.TripPaymentCaptureService;
import com.rentflow.trip.dto.CheckInRequest;
import com.rentflow.trip.dto.CheckOutRequest;
import com.rentflow.trip.dto.TripRecordResponse;
import com.rentflow.trip.entity.TripRecord;
import com.rentflow.trip.repository.TripRecordRepository;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class TripService {

    private final BookingRepository bookingRepository;
    private final ListingRepository listingRepository;
    private final VehicleRepository vehicleRepository;
    private final TripRecordRepository tripRecordRepository;
    private final TripPaymentCaptureService tripPaymentCaptureService;
    private final SecurityContext securityContext;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public TripService(
            BookingRepository bookingRepository,
            ListingRepository listingRepository,
            VehicleRepository vehicleRepository,
            TripRecordRepository tripRecordRepository,
            TripPaymentCaptureService tripPaymentCaptureService,
            SecurityContext securityContext,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.listingRepository = listingRepository;
        this.vehicleRepository = vehicleRepository;
        this.tripRecordRepository = tripRecordRepository;
        this.tripPaymentCaptureService = tripPaymentCaptureService;
        this.securityContext = securityContext;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public TripRecordResponse checkIn(UUID bookingId, CheckInRequest request) {
        UUID actorId = securityContext.currentUserId();
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        requireCustomerOwner(booking, actorId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking must be CONFIRMED for check-in");
        }

        Listing listing = listingRepository.findById(booking.getListingId())
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Listing must be ACTIVE for check-in");
        }
        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId())
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Vehicle must be ACTIVE for check-in");
        }

        if (tripRecordRepository.findByBookingId(bookingId).isPresent()) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Trip record already exists");
        }

        Instant now = clock.instant();
        TripRecord tripRecord = new TripRecord();
        tripRecord.setBookingId(bookingId);
        tripRecord.setCustomerId(actorId);
        tripRecord.setCheckInAt(now);
        tripRecord.setCheckInOdometer(request.odometer());
        tripRecord.setCheckInFuelLevel(request.fuelLevel());
        tripRecord.setNotes(request.note());
        tripRecordRepository.save(tripRecord);

        booking.setStatus(BookingStatus.IN_PROGRESS);
        bookingRepository.save(booking);
        return toResponse(booking, tripRecord);
    }

    public TripRecordResponse checkOut(UUID bookingId, CheckOutRequest request) {
        UUID actorId = securityContext.currentUserId();
        PreparedCheckOut prepared = required(transactionTemplate.execute(status ->
                prepareCheckOut(bookingId, request, actorId)));

        tripPaymentCaptureService.captureRemainingForBooking(prepared.bookingId());

        return required(transactionTemplate.execute(status ->
                finalizeCheckOut(prepared, request, actorId)));
    }

    private PreparedCheckOut prepareCheckOut(UUID bookingId, CheckOutRequest request, UUID actorId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        requireCustomerOwner(booking, actorId);
        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking must be IN_PROGRESS for check-out");
        }

        TripRecord tripRecord = tripRecordRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new BusinessRuleException("BOOKING_INVALID_STATUS", "Trip record not found for check-out"));
        if (tripRecord.getCheckOutAt() != null) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking already checked out");
        }
        if (request.odometer() < tripRecord.getCheckInOdometer()) {
            throw new BusinessRuleException("VALIDATION_ERROR", "Check-out odometer must be >= check-in odometer");
        }
        return new PreparedCheckOut(booking.getId(), tripRecord.getId(), tripRecord.getCheckInOdometer());
    }

    private TripRecordResponse finalizeCheckOut(PreparedCheckOut prepared, CheckOutRequest request, UUID actorId) {
        Booking booking = bookingRepository.findByIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(prepared.bookingId().toString()));
        requireCustomerOwner(booking, actorId);
        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking must be IN_PROGRESS for check-out");
        }

        TripRecord tripRecord = tripRecordRepository.findByBookingIdForUpdate(prepared.bookingId())
                .orElseThrow(() -> new BusinessRuleException("BOOKING_INVALID_STATUS", "Trip record not found for check-out"));
        if (!Objects.equals(prepared.tripRecordId(), tripRecord.getId())) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Trip record changed during check-out");
        }
        if (tripRecord.getCheckOutAt() != null) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Booking already checked out");
        }
        if (!prepared.checkInOdometer().equals(tripRecord.getCheckInOdometer())
                || request.odometer() < tripRecord.getCheckInOdometer()) {
            throw new BusinessRuleException("VALIDATION_ERROR", "Check-out odometer must be >= check-in odometer");
        }

        tripRecord.setCheckOutAt(clock.instant());
        tripRecord.setCheckOutOdometer(request.odometer());
        tripRecord.setCheckOutFuelLevel(request.fuelLevel());
        if (request.note() != null && !request.note().isBlank()) {
            tripRecord.setNotes(request.note());
        }
        tripRecordRepository.save(tripRecord);

        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);
        return toResponse(booking, tripRecord);
    }

    private <T> T required(T value) {
        if (value == null) {
            throw new IllegalStateException("Transactional callback returned null unexpectedly");
        }
        return value;
    }

    private void requireCustomerOwner(Booking booking, UUID actorId) {
        if (!booking.getCustomerId().equals(actorId)) {
            throw new BookingNotFoundException(booking.getId().toString());
        }
    }

    private TripRecordResponse toResponse(Booking booking, TripRecord tripRecord) {
        return new TripRecordResponse(
                booking.getId(),
                booking.getStatus(),
                tripRecord.getCheckInAt(),
                tripRecord.getCheckOutAt(),
                tripRecord.getCheckInOdometer(),
                tripRecord.getCheckOutOdometer(),
                tripRecord.getCheckInFuelLevel(),
                tripRecord.getCheckOutFuelLevel(),
                tripRecord.getNotes());
    }

    private record PreparedCheckOut(
            UUID bookingId,
            UUID tripRecordId,
            Integer checkInOdometer
    ) {
    }
}
