package com.rentflow.booking.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status = BookingStatus.HELD;

    @Column(name = "hold_token")
    private UUID holdToken;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "host_approval_expires_at")
    private Instant hostApprovalExpiresAt;

    @Column(name = "pickup_location", columnDefinition = "TEXT")
    private String pickupLocation;

    @Column(name = "return_location", columnDefinition = "TEXT")
    private String returnLocation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "price_snapshot", columnDefinition = "jsonb", nullable = false)
    private String priceSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", columnDefinition = "jsonb", nullable = false)
    private String policySnapshot;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
