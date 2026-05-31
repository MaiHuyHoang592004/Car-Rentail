package com.rentflow.vehicle;

import com.rentflow.common.util.EncryptionUtil;
import com.rentflow.vehicle.dto.VehicleResponse;
import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleCategory;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.mapper.VehicleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleMapperTest {

    @Mock
    private EncryptionUtil encryptionUtil;

    private VehicleMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new VehicleMapper(encryptionUtil);
    }

    @Test
    void toResponse_returnsReadableIdentifiersWhenDecryptSucceeds() {
        Vehicle vehicle = sampleVehicle();
        when(encryptionUtil.decrypt("enc-plate")).thenReturn("51H-123.45");
        when(encryptionUtil.decrypt("enc-vin")).thenReturn("VIN123");

        VehicleResponse response = mapper.toResponse(vehicle);

        assertThat(response.plateNumber()).isEqualTo("51H-123.45");
        assertThat(response.vin()).isEqualTo("VIN123");
        assertThat(response.identifierIntegrity().plateNumberReadable()).isTrue();
        assertThat(response.identifierIntegrity().vinReadable()).isTrue();
        assertThat(response.identifierIntegrity().hasUnreadableEncryptedFields()).isFalse();
    }

    @Test
    void toResponse_returnsNullPlateWhenPlateDecryptFails() {
        Vehicle vehicle = sampleVehicle();
        when(encryptionUtil.decrypt("enc-plate")).thenThrow(new RuntimeException("bad plate"));
        when(encryptionUtil.decrypt("enc-vin")).thenReturn("VIN123");

        VehicleResponse response = mapper.toResponse(vehicle);

        assertThat(response.plateNumber()).isNull();
        assertThat(response.vin()).isEqualTo("VIN123");
        assertThat(response.identifierIntegrity().plateNumberReadable()).isFalse();
        assertThat(response.identifierIntegrity().vinReadable()).isTrue();
        assertThat(response.identifierIntegrity().hasUnreadableEncryptedFields()).isTrue();
    }

    @Test
    void toResponse_returnsNullVinWhenVinDecryptFails() {
        Vehicle vehicle = sampleVehicle();
        when(encryptionUtil.decrypt("enc-plate")).thenReturn("51H-123.45");
        when(encryptionUtil.decrypt("enc-vin")).thenThrow(new RuntimeException("bad vin"));

        VehicleResponse response = mapper.toResponse(vehicle);

        assertThat(response.plateNumber()).isEqualTo("51H-123.45");
        assertThat(response.vin()).isNull();
        assertThat(response.identifierIntegrity().plateNumberReadable()).isTrue();
        assertThat(response.identifierIntegrity().vinReadable()).isFalse();
        assertThat(response.identifierIntegrity().hasUnreadableEncryptedFields()).isTrue();
    }

    @Test
    void toResponse_marksBothUnreadableWhenBothDecryptionsFail() {
        Vehicle vehicle = sampleVehicle();
        when(encryptionUtil.decrypt("enc-plate")).thenThrow(new RuntimeException("bad plate"));
        when(encryptionUtil.decrypt("enc-vin")).thenThrow(new RuntimeException("bad vin"));

        VehicleResponse response = mapper.toResponse(vehicle);

        assertThat(response.plateNumber()).isNull();
        assertThat(response.vin()).isNull();
        assertThat(response.identifierIntegrity().plateNumberReadable()).isFalse();
        assertThat(response.identifierIntegrity().vinReadable()).isFalse();
        assertThat(response.identifierIntegrity().hasUnreadableEncryptedFields()).isTrue();
    }

    private Vehicle sampleVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setCategory(VehicleCategory.SEDAN);
        vehicle.setMake("Toyota");
        vehicle.setModel("Vios");
        vehicle.setManufactureYear(2022);
        vehicle.setTransmission(TransmissionType.AUTO);
        vehicle.setFuelType(FuelType.PETROL);
        vehicle.setSeats(5);
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle.setCity("Hanoi");
        vehicle.setPlateNumberEncrypted("enc-plate");
        vehicle.setVinEncrypted("enc-vin");
        return vehicle;
    }
}
