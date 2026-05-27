package com.rentflow.payment.repository;

import com.rentflow.payment.entity.BookingPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingPaymentRepository extends JpaRepository<BookingPayment, UUID> {

    Optional<BookingPayment> findByBookingId(UUID bookingId);
}
