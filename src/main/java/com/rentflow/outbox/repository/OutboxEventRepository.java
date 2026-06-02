package com.rentflow.outbox.repository;

import com.rentflow.outbox.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE (
                status IN ('PENDING', 'RETRY')
                AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
              )
              OR (
                status = 'PROCESSING'
                AND processing_started_at IS NOT NULL
                AND processing_started_at <= :staleBefore
              )
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPublishCandidatesForClaim(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("batchSize") int batchSize);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT oe FROM OutboxEvent oe WHERE oe.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") UUID id);
}
