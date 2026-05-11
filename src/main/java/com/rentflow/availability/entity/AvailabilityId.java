package com.rentflow.availability.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class AvailabilityId implements Serializable {

    private UUID listingId;
    private LocalDate availableDate;

    public AvailabilityId() {}

    public AvailabilityId(UUID listingId, LocalDate availableDate) {
        this.listingId = listingId;
        this.availableDate = availableDate;
    }

    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }

    public LocalDate getAvailableDate() { return availableDate; }
    public void setAvailableDate(LocalDate availableDate) { this.availableDate = availableDate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AvailabilityId that = (AvailabilityId) o;
        return Objects.equals(listingId, that.listingId) &&
               Objects.equals(availableDate, that.availableDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listingId, availableDate);
    }
}
