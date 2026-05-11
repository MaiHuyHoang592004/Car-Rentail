package com.rentflow.scheduler;

import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Component
public class ExpireHeldBookingsProcessor {

    private final BookingRepository bookingRepository;
    private final AvailabilityCalendarRepository availabilityCalendarRepository;
    private final Clock clock;

    public ExpireHeldBookingsProcessor(
            BookingRepository bookingRepository,
            AvailabilityCalendarRepository availabilityCalendarRepository,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.availabilityCalendarRepository = availabilityCalendarRepository;
        this.clock = clock;
    }

    @Transactional
    public int processBatch(int batchSize) {
        List<Booking> expiredBookings = bookingRepository.findExpiredHeldBookingsForUpdate(
                clock.instant(),
                batchSize);
        int expiredCount = 0;
        for (Booking booking : expiredBookings) {
            if (booking.getStatus() != BookingStatus.HELD) {
                continue;
            }

            List<AvailabilityCalendar> availabilityRows = availabilityCalendarRepository.findForBookingRangeForUpdate(
                    booking.getListingId(),
                    booking.getPickupDate(),
                    booking.getReturnDate());
            booking.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);

            List<AvailabilityCalendar> releasedRows = releaseHeldAvailability(availabilityRows, booking);
            if (!releasedRows.isEmpty()) {
                availabilityCalendarRepository.saveAll(releasedRows);
            }
            expiredCount++;
        }
        return expiredCount;
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
}
