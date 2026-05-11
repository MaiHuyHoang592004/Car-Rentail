package com.rentflow.booking.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class BookingExtraId implements Serializable {

    private UUID bookingId;
    private UUID extraId;

    public BookingExtraId() {}

    public BookingExtraId(UUID bookingId, UUID extraId) {
        this.bookingId = bookingId;
        this.extraId = extraId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public UUID getExtraId() {
        return extraId;
    }

    public void setExtraId(UUID extraId) {
        this.extraId = extraId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookingExtraId that = (BookingExtraId) o;
        return Objects.equals(bookingId, that.bookingId)
                && Objects.equals(extraId, that.extraId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookingId, extraId);
    }
}
