package com.rentflow.outbox.repository;

import com.rentflow.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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
                AND next_attempt_at <= :now
              )
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPublishCandidatesForUpdate(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize);

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE id = :id
            FOR UPDATE
            """, nativeQuery = true)
    java.util.Optional<OutboxEvent> findByIdForUpdate(@Param("id") UUID id);
}
