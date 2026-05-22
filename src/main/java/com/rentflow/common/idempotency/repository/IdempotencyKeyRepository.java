package com.rentflow.common.idempotency.repository;

import com.rentflow.common.idempotency.entity.IdempotencyKey;
import com.rentflow.common.idempotency.service.IdempotencyScope;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (
                id, user_id, scope, "key", request_hash, status,
                locked_until, expires_at, created_at, updated_at
            )
            VALUES (
                :id, :userId, :#{#scope.name()}, :key, :requestHash, 'PROCESSING',
                :lockedUntil, :expiresAt, NOW(), NOW()
            )
            ON CONFLICT (user_id, scope, "key") DO NOTHING
            """, nativeQuery = true)
    int insertProcessingIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("scope") IdempotencyScope scope,
            @Param("key") String key,
            @Param("requestHash") String requestHash,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("expiresAt") Instant expiresAt);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ik FROM IdempotencyKey ik WHERE ik.id = :id")
    Optional<IdempotencyKey> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            DELETE FROM idempotency_keys
            WHERE id IN (
                SELECT id FROM idempotency_keys
                WHERE expires_at < :cutoff
                ORDER BY expires_at ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int deleteExpiredKeys(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
