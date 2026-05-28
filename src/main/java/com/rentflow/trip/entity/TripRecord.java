package com.rentflow.trip.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_records")
@Getter
@Setter
public class TripRecord extends BaseEntity {

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "check_in_at", nullable = false)
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Column(name = "check_in_odometer", nullable = false)
    private Integer checkInOdometer;

    @Column(name = "check_out_odometer")
    private Integer checkOutOdometer;

    @Column(name = "check_in_fuel_level", nullable = false)
    private Integer checkInFuelLevel;

    @Column(name = "check_out_fuel_level")
    private Integer checkOutFuelLevel;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
