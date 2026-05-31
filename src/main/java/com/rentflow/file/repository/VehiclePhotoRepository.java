package com.rentflow.file.repository;

import com.rentflow.file.entity.VehiclePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehiclePhotoRepository extends JpaRepository<VehiclePhoto, UUID> {

    long countByVehicleId(UUID vehicleId);

    List<VehiclePhoto> findByVehicleIdOrderByDisplayOrderAsc(UUID vehicleId);

    Optional<VehiclePhoto> findByIdAndVehicleId(UUID id, UUID vehicleId);

    boolean existsByFileId(UUID fileId);
}
