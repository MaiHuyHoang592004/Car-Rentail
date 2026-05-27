package com.rentflow.payment.repository;

import com.rentflow.payment.entity.BookingPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingPaymentRepository extends JpaRepository<BookingPayment, UUID> {

    Optional<BookingPayment> findByBookingId(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bp FROM BookingPayment bp WHERE bp.bookingId = :bookingId")
    Optional<BookingPayment> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bp FROM BookingPayment bp WHERE bp.id = :id")
    Optional<BookingPayment> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT *
            FROM booking_payments
            WHERE void_retry_required = TRUE
              AND provider = 'COREBANK'
              AND (void_retry_next_at IS NULL OR void_retry_next_at <= :now)
              AND void_retry_count < :maxAttempts
            ORDER BY updated_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<BookingPayment> findVoidRetryCandidatesForUpdate(
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("batchSize") int batchSize);
}
