package com.rentflow.dispute.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "dispute_attachments")
@Getter
@Setter
public class DisputeAttachment extends BaseEntity {

    @Column(name = "dispute_id", nullable = false)
    private UUID disputeId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
