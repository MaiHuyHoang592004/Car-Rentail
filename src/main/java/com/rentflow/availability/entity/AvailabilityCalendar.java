package com.rentflow.availability.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "availability_calendar")
@IdClass(AvailabilityId.class)
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class AvailabilityCalendar {

    @Id
    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Id
    @Column(name = "available_date", nullable = false)
    private LocalDate availableDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilityStatus status = AvailabilityStatus.FREE;

    @Column(name = "hold_token")
    private UUID holdToken;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AvailabilityCalendar() {}

    public AvailabilityCalendar(UUID listingId, LocalDate availableDate) {
        this.listingId = listingId;
        this.availableDate = availableDate;
        this.status = AvailabilityStatus.FREE;
    }
}
