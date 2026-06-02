package com.rentflow.trip.service;

import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private TripRecordRepository tripRecordRepository;
    @Mock private TripPaymentCaptureService tripPaymentCaptureService;
    @Mock private SecurityContext securityContext;

    private TripService tripService;
    private Clock clock;
    private AtomicInteger transactionCommits;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-05-29T00:00:00Z"), ZoneOffset.UTC);
        transactionCommits = new AtomicInteger();
        tripService = new TripService(
                bookingRepository,
                listingRepository,
                vehicleRepository,
                tripRecordRepository,
                tripPaymentCaptureService,
                securityContext,
                clock,
                transactionManager());
    }

    @Test
    void checkInTransitionsBookingToInProgress() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        Booking booking = booking(bookingId, customerId, listingId, BookingStatus.CONFIRMED);
        Listing listing = listing(listingId, vehicleId, ListingStatus.ACTIVE);
        Vehicle vehicle = vehicle(vehicleId, VehicleStatus.ACTIVE);

        when(securityContext.currentUserId()).thenReturn(customerId);
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(tripRecordRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
        when(tripRecordRepository.save(any(TripRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TripRecordResponse response = tripService.checkIn(bookingId, new CheckInRequest(12345, 80, "start trip"));

        assertThat(response.bookingStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        assertThat(response.checkInAt()).isEqualTo(Instant.parse("2026-05-29T00:00:00Z"));
        verify(tripRecordRepository).save(any(TripRecord.class));
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void checkOutCapturesPaymentAndCompletesBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Booking booking = booking(bookingId, customerId, listingId, BookingStatus.IN_PROGRESS);
        TripRecord record = new TripRecord();
        record.setId(UUID.randomUUID());
        record.setBookingId(bookingId);
        record.setCustomerId(customerId);
        record.setCheckInAt(Instant.parse("2026-05-28T00:00:00Z"));
        record.setCheckInOdometer(12345);
        record.setCheckInFuelLevel(80);
        record.setNotes("start trip");

        when(securityContext.currentUserId()).thenReturn(customerId);
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking), Optional.of(booking));
        when(tripRecordRepository.findByBookingIdForUpdate(bookingId)).thenReturn(Optional.of(record), Optional.of(record));
        when(tripRecordRepository.save(any(TripRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            assertThat(transactionCommits).hasValue(1);
            assertThat(record.getCheckOutAt()).isNull();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
            return null;
        }).when(tripPaymentCaptureService).captureRemainingForBooking(bookingId);

        TripRecordResponse response = tripService.checkOut(bookingId, new CheckOutRequest(12500, 70, "end trip"));

        assertThat(response.bookingStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(response.checkOutAt()).isEqualTo(Instant.parse("2026-05-29T00:00:00Z"));
        assertThat(response.checkOutOdometer()).isEqualTo(12500);
        assertThat(transactionCommits).hasValue(2);
        verify(tripPaymentCaptureService).captureRemainingForBooking(bookingId);
        verify(tripRecordRepository).save(any(TripRecord.class));
        verify(bookingRepository).save(any(Booking.class));

        InOrder inOrder = inOrder(bookingRepository, tripRecordRepository, tripPaymentCaptureService);
        inOrder.verify(bookingRepository).findByIdForUpdate(bookingId);
        inOrder.verify(tripRecordRepository).findByBookingIdForUpdate(bookingId);
        inOrder.verify(tripPaymentCaptureService).captureRemainingForBooking(bookingId);
        inOrder.verify(bookingRepository).findByIdForUpdate(bookingId);
        inOrder.verify(tripRecordRepository).findByBookingIdForUpdate(bookingId);
    }

    @Test
    void checkOutDoesNotCompleteTripWhenStateDriftsAfterCapture() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Booking booking = booking(bookingId, customerId, listingId, BookingStatus.IN_PROGRESS);
        TripRecord record = new TripRecord();
        record.setId(UUID.randomUUID());
        record.setBookingId(bookingId);
        record.setCustomerId(customerId);
        record.setCheckInAt(Instant.parse("2026-05-28T00:00:00Z"));
        record.setCheckInOdometer(12345);
        record.setCheckInFuelLevel(80);

        when(securityContext.currentUserId()).thenReturn(customerId);
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking), Optional.of(booking));
        when(tripRecordRepository.findByBookingIdForUpdate(bookingId)).thenReturn(Optional.of(record));
        doAnswer(invocation -> {
            booking.setStatus(BookingStatus.CANCELLED);
            return null;
        }).when(tripPaymentCaptureService).captureRemainingForBooking(bookingId);

        assertThatThrownBy(() -> tripService.checkOut(bookingId, new CheckOutRequest(12500, 70, "end trip")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_INVALID_STATUS");

        assertThat(record.getCheckOutAt()).isNull();
        assertThat(record.getCheckOutOdometer()).isNull();
        verify(tripPaymentCaptureService).captureRemainingForBooking(bookingId);
        verify(tripRecordRepository, never()).save(any(TripRecord.class));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    private Booking booking(UUID bookingId, UUID customerId, UUID listingId, BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setCustomerId(customerId);
        booking.setListingId(listingId);
        booking.setStatus(status);
        return booking;
    }

    private Listing listing(UUID listingId, UUID vehicleId, ListingStatus status) {
        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setVehicleId(vehicleId);
        listing.setStatus(status);
        return listing;
    }

    private Vehicle vehicle(UUID vehicleId, VehicleStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setStatus(status);
        return vehicle;
    }

    private PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                transactionCommits.incrementAndGet();
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
