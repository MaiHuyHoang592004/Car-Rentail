package com.rentflow.dispute.repository;

import com.rentflow.dispute.entity.DisputeAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisputeAttachmentRepository extends JpaRepository<DisputeAttachment, UUID> {

    List<DisputeAttachment> findByDisputeId(UUID disputeId);
}
