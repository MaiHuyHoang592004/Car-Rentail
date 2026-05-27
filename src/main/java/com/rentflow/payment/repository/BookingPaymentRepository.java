package com.rentflow.payment.repository;

import com.rentflow.payment.entity.BookingPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingPaymentRepository extends JpaRepository<BookingPayment, UUID> {

    Optional<BookingPayment> findByBookingId(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bp FROM BookingPayment bp WHERE bp.bookingId = :bookingId")
    Optional<BookingPayment> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);
}
