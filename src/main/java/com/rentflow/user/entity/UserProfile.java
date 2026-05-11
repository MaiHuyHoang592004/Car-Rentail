package com.rentflow.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "address_line", columnDefinition = "TEXT")
    private String addressLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "driver_verification_status", nullable = false, length = 20)
    private DriverVerificationStatus driverVerificationStatus = DriverVerificationStatus.NOT_SUBMITTED;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private com.rentflow.auth.entity.AuthUser user;

    public UserProfile() {
    }

    public UserProfile(String fullName) {
        this.fullName = fullName;
        this.driverVerificationStatus = DriverVerificationStatus.NOT_SUBMITTED;
    }

    public enum DriverVerificationStatus {
        NOT_SUBMITTED,
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED
    }
}
