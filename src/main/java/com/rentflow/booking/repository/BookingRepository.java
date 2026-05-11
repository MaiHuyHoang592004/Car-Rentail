package com.rentflow.booking.repository;

import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.customerId = :customerId
              AND b.status IN :activeStatuses
              AND b.pickupDate < :returnDate
              AND b.returnDate > :pickupDate
            """)
    boolean existsOverlappingActiveBooking(
            @Param("customerId") UUID customerId,
            @Param("pickupDate") LocalDate pickupDate,
            @Param("returnDate") LocalDate returnDate,
            @Param("activeStatuses") List<BookingStatus> activeStatuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT *
            FROM bookings
            WHERE status = 'HELD'
              AND hold_expires_at < :now
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Booking> findExpiredHeldBookingsForUpdate(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize);

    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            JOIN Listing l ON l.id = b.listingId
            WHERE l.vehicleId = :vehicleId
              AND b.status IN :activeStatuses
            """)
    boolean existsActiveBookingsForVehicle(
            @Param("vehicleId") UUID vehicleId,
            @Param("activeStatuses") List<BookingStatus> activeStatuses);

    Page<Booking> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<Booking> findByCustomerIdAndStatusOrderByCreatedAtDesc(
            UUID customerId,
            BookingStatus status,
            Pageable pageable);
}
