package com.rentflow.trip.repository;

import com.rentflow.trip.entity.TripRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TripRecordRepository extends JpaRepository<TripRecord, UUID> {

    Optional<TripRecord> findByBookingId(UUID bookingId);
}
