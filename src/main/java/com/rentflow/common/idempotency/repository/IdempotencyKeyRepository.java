package com.rentflow.common.idempotency.repository;

import com.rentflow.common.idempotency.entity.IdempotencyKey;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ik FROM IdempotencyKey ik
            WHERE ik.userId = :userId
              AND ik.scope = :scope
              AND ik.key = :key
            """)
    Optional<IdempotencyKey> findByUserIdAndScopeAndKeyForUpdate(
            @Param("userId") UUID userId,
            @Param("scope") IdempotencyScope scope,
            @Param("key") String key);
}
