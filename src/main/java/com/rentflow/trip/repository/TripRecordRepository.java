package com.rentflow.trip.repository;

import com.rentflow.trip.entity.TripRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TripRecordRepository extends JpaRepository<TripRecord, UUID> {

    Optional<TripRecord> findByBookingId(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tr FROM TripRecord tr WHERE tr.bookingId = :bookingId")
    Optional<TripRecord> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);
}
