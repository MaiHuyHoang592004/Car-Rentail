package com.rentflow.payment.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.PaymentNotFoundException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.payment.dto.PaymentDetailResponse;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentTransaction;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentQueryService {

    private final BookingRepository bookingRepository;
    private final BookingPaymentRepository bookingPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SecurityContext securityContext;
    private final PaymentDetailResponseFactory paymentDetailResponseFactory;

    public PaymentQueryService(
            BookingRepository bookingRepository,
            BookingPaymentRepository bookingPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            SecurityContext securityContext,
            PaymentDetailResponseFactory paymentDetailResponseFactory) {
        this.bookingRepository = bookingRepository;
        this.bookingPaymentRepository = bookingPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.securityContext = securityContext;
        this.paymentDetailResponseFactory = paymentDetailResponseFactory;
    }

    @Transactional(readOnly = true)
    public PaymentDetailResponse getByBookingId(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(String.valueOf(bookingId)));
        requireCanRead(booking);
        BookingPayment payment = bookingPaymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(bookingId)));
        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId());
        return paymentDetailResponseFactory.create(booking, payment, transactions);
    }

    private void requireCanRead(Booking booking) {
        UUID currentUserId = securityContext.currentUserId();
        if (securityContext.hasRole(Role.ADMIN)) {
            return;
        }
        if (booking.getCustomerId().equals(currentUserId) || booking.getHostId().equals(currentUserId)) {
            return;
        }
        throw new BookingNotFoundException(String.valueOf(booking.getId()));
    }
}
