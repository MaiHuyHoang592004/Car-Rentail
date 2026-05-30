package com.rentflow.file.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "vehicle_photos")
@Getter
@Setter
public class VehiclePhoto extends BaseEntity {

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
