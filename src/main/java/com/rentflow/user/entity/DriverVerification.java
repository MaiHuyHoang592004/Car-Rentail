package com.rentflow.user.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "driver_verifications")
@Getter
@Setter
public class DriverVerification extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriverVerificationStatus status = DriverVerificationStatus.PENDING;

    @Column(name = "license_number_encrypted", nullable = false, length = 1000)
    private String licenseNumberEncrypted;

    @Column(name = "license_number_hash", nullable = false, length = 128)
    private String licenseNumberHash;

    @Column(name = "license_expiry_date", nullable = false)
    private LocalDate licenseExpiryDate;

    @Column(name = "document_file_id")
    private UUID documentFileId;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
