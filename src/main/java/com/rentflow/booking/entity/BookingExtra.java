package com.rentflow.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "booking_extras")
@IdClass(BookingExtraId.class)
@Getter
@Setter
public class BookingExtra {

    @Id
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Id
    @Column(name = "extra_id", nullable = false)
    private UUID extraId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceSnapshot;
}
