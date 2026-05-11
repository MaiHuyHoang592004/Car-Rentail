package com.rentflow.scheduler;

import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpireHeldBookingsProcessorTest {

    private static final Instant NOW = Instant.parse("2026-05-11T00:00:00Z");
    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_BOOKING_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID LISTING_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID HOLD_TOKEN = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private BookingRepository bookingRepository;
    private AvailabilityCalendarRepository availabilityRepository;
    private ExpireHeldBookingsProcessor processor;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        availabilityRepository = mock(AvailabilityCalendarRepository.class);
        processor = new ExpireHeldBookingsProcessor(
                bookingRepository,
                availabilityRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void expiredHeldBookingBecomesExpiredAndMatchingAvailabilityIsReleased() {
        Booking booking = booking(BookingStatus.HELD);
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking);
        when(bookingRepository.findExpiredHeldBookingsForUpdate(NOW, 100)).thenReturn(List.of(booking));
        when(availabilityRepository.findForBookingRangeForUpdate(
                LISTING_ID, booking.getPickupDate(), booking.getReturnDate()))
                .thenReturn(rows);

        int processed = processor.processBatch(100);

        assertThat(processed).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(AvailabilityStatus.FREE);
            assertThat(row.getBookingId()).isNull();
            assertThat(row.getHoldToken()).isNull();
            assertThat(row.getHoldExpiresAt()).isNull();
        });
        verify(bookingRepository).save(booking);
        verify(availabilityRepository).saveAll(rows);
    }

    @Test
    void nonExpiredBookingsAreNotProcessedWhenRepositoryReturnsNoRows() {
        when(bookingRepository.findExpiredHeldBookingsForUpdate(NOW, 100)).thenReturn(List.of());

        int processed = processor.processBatch(100);

        assertThat(processed).isZero();
        verify(bookingRepository, never()).save(any());
        verify(availabilityRepository, never()).findForBookingRangeForUpdate(any(), any(), any());
        verify(availabilityRepository, never()).saveAll(any());
    }

    @Test
    void repeatedRunIsSafeWhenSecondRunFindsNoRows() {
        Booking booking = booking(BookingStatus.HELD);
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking);
        when(bookingRepository.findExpiredHeldBookingsForUpdate(NOW, 100))
                .thenReturn(List.of(booking))
                .thenReturn(List.of());
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(rows);

        int first = processor.processBatch(100);
        int second = processor.processBatch(100);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        verify(bookingRepository).save(booking);
        verify(availabilityRepository).saveAll(rows);
    }

    @Test
    void alreadyNonHeldBookingFromSecondInvocationIsSkipped() {
        Booking booking = booking(BookingStatus.HELD);
        List<AvailabilityCalendar> rows = heldAvailabilityRows(booking);
        when(bookingRepository.findExpiredHeldBookingsForUpdate(NOW, 100))
                .thenReturn(List.of(booking))
                .thenReturn(List.of(booking));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any())).thenReturn(rows);

        int first = processor.processBatch(100);
        int second = processor.processBatch(100);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        verify(bookingRepository).save(booking);
    }

    @Test
    void nonMatchingAvailabilityRowsAreNotReleased() {
        Booking booking = booking(BookingStatus.HELD);
        AvailabilityCalendar matching = heldAvailabilityRows(booking).get(0);
        AvailabilityCalendar wrongBooking = row(LocalDate.of(2026, 6, 2), AvailabilityStatus.HOLD, OTHER_BOOKING_ID, HOLD_TOKEN);
        AvailabilityCalendar blocked = row(LocalDate.of(2026, 6, 3), AvailabilityStatus.BLOCKED, BOOKING_ID, HOLD_TOKEN);
        AvailabilityCalendar wrongToken = row(LocalDate.of(2026, 6, 4), AvailabilityStatus.HOLD, BOOKING_ID, UUID.randomUUID());
        when(bookingRepository.findExpiredHeldBookingsForUpdate(NOW, 100)).thenReturn(List.of(booking));
        when(availabilityRepository.findForBookingRangeForUpdate(any(), any(), any()))
                .thenReturn(List.of(matching, wrongBooking, blocked, wrongToken));

        int processed = processor.processBatch(100);

        assertThat(processed).isEqualTo(1);
        assertThat(matching.getStatus()).isEqualTo(AvailabilityStatus.FREE);
        assertThat(matching.getBookingId()).isNull();
        assertThat(wrongBooking.getStatus()).isEqualTo(AvailabilityStatus.HOLD);
        assertThat(wrongBooking.getBookingId()).isEqualTo(OTHER_BOOKING_ID);
        assertThat(blocked.getStatus()).isEqualTo(AvailabilityStatus.BLOCKED);
        assertThat(blocked.getBookingId()).isEqualTo(BOOKING_ID);
        assertThat(wrongToken.getStatus()).isEqualTo(AvailabilityStatus.HOLD);
        assertThat(wrongToken.getHoldToken()).isNotEqualTo(HOLD_TOKEN);
        verify(availabilityRepository).saveAll(List.of(matching));
    }

    private Booking booking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setListingId(LISTING_ID);
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setStatus(status);
        booking.setHoldToken(HOLD_TOKEN);
        booking.setHoldExpiresAt(NOW.minusSeconds(60));
        return booking;
    }

    private List<AvailabilityCalendar> heldAvailabilityRows(Booking booking) {
        return List.of(
                row(LocalDate.of(2026, 6, 1), AvailabilityStatus.HOLD, booking.getId(), booking.getHoldToken()),
                row(LocalDate.of(2026, 6, 2), AvailabilityStatus.HOLD, booking.getId(), booking.getHoldToken()));
    }

    private AvailabilityCalendar row(
            LocalDate date,
            AvailabilityStatus status,
            UUID bookingId,
            UUID holdToken) {
        AvailabilityCalendar row = new AvailabilityCalendar(LISTING_ID, date);
        row.setStatus(status);
        row.setBookingId(bookingId);
        row.setHoldToken(holdToken);
        row.setHoldExpiresAt(NOW.minusSeconds(60));
        return row;
    }
}
