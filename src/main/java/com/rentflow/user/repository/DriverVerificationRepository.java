package com.rentflow.user.repository;

import com.rentflow.user.entity.DriverVerification;
import com.rentflow.user.entity.DriverVerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverVerificationRepository extends JpaRepository<DriverVerification, UUID> {

    boolean existsByCustomerIdAndStatusIn(UUID customerId, Collection<DriverVerificationStatus> statuses);

    Page<DriverVerification> findByStatus(DriverVerificationStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT dv FROM DriverVerification dv WHERE dv.id = :id")
    Optional<DriverVerification> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT *
            FROM driver_verifications
            WHERE status IN ('PENDING', 'APPROVED')
              AND license_expiry_date < :currentDate
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<DriverVerification> findExpiredCandidatesForUpdate(
            @Param("currentDate") LocalDate currentDate,
            @Param("batchSize") int batchSize);
}
