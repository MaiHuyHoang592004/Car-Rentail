package com.rentflow.payment.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "booking_payments")
@Getter
@Setter
public class BookingPayment extends BaseEntity {

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "selected_bank_id")
    private UUID selectedBankId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 40)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProviderType provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentStatus status = PaymentStatus.UNPAID;

    @Column(name = "authorized_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal authorizedAmount = BigDecimal.ZERO;

    @Column(name = "captured_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal capturedAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "external_order_ref", unique = true, length = 128)
    private String externalOrderRef;

    @Column(name = "provider_payment_order_id", length = 120)
    private String providerPaymentOrderId;

    @Column(name = "provider_hold_id", length = 120)
    private String providerHoldId;

    @Column(name = "provider_status", length = 80)
    private String providerStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_metadata", columnDefinition = "jsonb")
    private String providerMetadata;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
