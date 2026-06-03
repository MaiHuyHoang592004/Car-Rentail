package com.rentflow.trip.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "trip_checkout_finalization_failures")
@Getter
@Setter
public class TripCheckoutFinalizationFailure extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "trip_record_id", nullable = false)
    private UUID tripRecordId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "captured_payment_id")
    private UUID capturedPaymentId;

    @Column(name = "check_in_odometer", nullable = false)
    private Integer checkInOdometer;

    @Column(name = "check_out_odometer", nullable = false)
    private Integer checkOutOdometer;

    @Column(name = "check_out_fuel_level", nullable = false)
    private Integer checkOutFuelLevel;

    @Column(name = "check_out_note", columnDefinition = "TEXT")
    private String checkOutNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripCheckoutFinalizationFailureStatus status = TripCheckoutFinalizationFailureStatus.PENDING;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
