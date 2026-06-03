package com.rentflow.trip.repository;

import com.rentflow.trip.entity.TripCheckoutFinalizationFailure;
import com.rentflow.trip.entity.TripCheckoutFinalizationFailureStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface TripCheckoutFinalizationFailureRepository
        extends JpaRepository<TripCheckoutFinalizationFailure, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TripCheckoutFinalizationFailure> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
            UUID bookingId,
            TripCheckoutFinalizationFailureStatus status);
}
