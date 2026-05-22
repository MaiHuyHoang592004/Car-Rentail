package com.rentflow.vehicle.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle extends BaseEntity {

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VehicleCategory category;

    @Column(nullable = false, length = 50)
    private String make;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(name = "manufacture_year", nullable = false)
    private Integer manufactureYear;

    @Column(name = "plate_number_encrypted", columnDefinition = "TEXT")
    private String plateNumberEncrypted;

    @Column(name = "plate_number_hash", length = 128)
    private String plateNumberHash;

    @Column(name = "vin_encrypted", columnDefinition = "TEXT")
    private String vinEncrypted;

    @Column(name = "vin_hash", length = 128)
    private String vinHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransmissionType transmission;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 20)
    private FuelType fuelType;

    @Column(nullable = false)
    private Integer seats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status = VehicleStatus.ACTIVE;

    @Column(name = "city", length = 100)
    private String city;

    public Vehicle() {
        this.status = VehicleStatus.ACTIVE;
    }

    public UUID getHostId() { return hostId; }
    public void setHostId(UUID hostId) { this.hostId = hostId; }

    public VehicleCategory getCategory() { return category; }
    public void setCategory(VehicleCategory category) { this.category = category; }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getManufactureYear() { return manufactureYear; }
    public void setManufactureYear(Integer year) { this.manufactureYear = year; }

    public String getPlateNumberEncrypted() { return plateNumberEncrypted; }
    public void setPlateNumberEncrypted(String plateNumberEncrypted) { this.plateNumberEncrypted = plateNumberEncrypted; }

    public String getPlateNumberHash() { return plateNumberHash; }
    public void setPlateNumberHash(String plateNumberHash) { this.plateNumberHash = plateNumberHash; }

    public String getVinEncrypted() { return vinEncrypted; }
    public void setVinEncrypted(String vinEncrypted) { this.vinEncrypted = vinEncrypted; }

    public String getVinHash() { return vinHash; }
    public void setVinHash(String vinHash) { this.vinHash = vinHash; }

    public TransmissionType getTransmission() { return transmission; }
    public void setTransmission(TransmissionType transmission) { this.transmission = transmission; }

    public FuelType getFuelType() { return fuelType; }
    public void setFuelType(FuelType fuelType) { this.fuelType = fuelType; }

    public Integer getSeats() { return seats; }
    public void setSeats(Integer seats) { this.seats = seats; }

    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
}
