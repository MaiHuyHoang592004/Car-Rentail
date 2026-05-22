package com.rentflow.booking.service;

import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * I24: pessimistic-lock + hold + release helpers extracted from BookingService.
 */
@Component
@RequiredArgsConstructor
public class AvailabilityReserver {

    private final AvailabilityCalendarRepository availabilityCalendarRepository;

    public List<AvailabilityCalendar> lockAndValidate(
            UUID listingId, LocalDate pickupDate, LocalDate returnDate, long rentalDays) {
        List<AvailabilityCalendar> rows = availabilityCalendarRepository.findForBookingRangeForUpdate(
                listingId, pickupDate, returnDate);
        if (rows.size() != rentalDays
                || rows.stream().anyMatch(row -> row.getStatus() != AvailabilityStatus.FREE)) {
            throw new BusinessRuleException(
                    "LISTING_NOT_AVAILABLE", "Listing is not available for selected dates");
        }
        return rows;
    }

    public void hold(List<AvailabilityCalendar> rows, UUID bookingId, UUID holdToken, Instant holdExpiresAt) {
        rows.forEach(row -> {
            row.setStatus(AvailabilityStatus.HOLD);
            row.setBookingId(bookingId);
            row.setHoldToken(holdToken);
            row.setHoldExpiresAt(holdExpiresAt);
        });
        availabilityCalendarRepository.saveAll(rows);
    }

    public List<AvailabilityCalendar> lockForBooking(Booking booking) {
        return availabilityCalendarRepository.findForBookingRangeForUpdate(
                booking.getListingId(), booking.getPickupDate(), booking.getReturnDate());
    }

    public List<AvailabilityCalendar> releaseHeld(List<AvailabilityCalendar> rows, Booking booking) {
        List<AvailabilityCalendar> released = rows.stream()
                .filter(row -> row.getStatus() == AvailabilityStatus.HOLD)
                .filter(row -> booking.getId().equals(row.getBookingId()))
                .filter(row -> booking.getHoldToken() == null
                        || booking.getHoldToken().equals(row.getHoldToken()))
                .peek(row -> {
                    row.setStatus(AvailabilityStatus.FREE);
                    row.setBookingId(null);
                    row.setHoldToken(null);
                    row.setHoldExpiresAt(null);
                })
                .toList();
        if (!released.isEmpty()) {
            availabilityCalendarRepository.saveAll(released);
        }
        return released;
    }
}
