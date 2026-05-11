package com.rentflow.vehicle.repository;

import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByIdAndHostId(UUID id, UUID hostId);

    Page<Vehicle> findByHostIdAndStatus(UUID hostId, VehicleStatus status, Pageable pageable);

    Page<Vehicle> findByHostId(UUID hostId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findById(@Param("id") UUID id);
}
