package com.rentflow.payment.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
public class PaymentTransaction extends BaseEntity {

    @Column(name = "booking_payment_id", nullable = false)
    private UUID bookingPaymentId;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentTransactionStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProviderType provider;

    @Column(name = "provider_request_id", length = 120)
    private String providerRequestId;

    @Column(name = "provider_ref", length = 120)
    private String providerRef;

    @Column(name = "provider_journal_id", length = 120)
    private String providerJournalId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_response", columnDefinition = "jsonb")
    private String providerResponse;

    @Column(name = "provider_error_code", length = 80)
    private String providerErrorCode;

    @Column(name = "provider_error_message", columnDefinition = "TEXT")
    private String providerErrorMessage;

    @Column(name = "idempotency_key_id")
    private UUID idempotencyKeyId;
}
