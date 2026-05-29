package com.rentflow.vehicle.dto;

import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.VehicleCategory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void createVehicle_blankCity_isInvalid() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                VehicleCategory.SEDAN,
                "Toyota",
                "Camry",
                2020,
                "ABC-123",
                null,
                TransmissionType.AUTO,
                FuelType.PETROL,
                5,
                null,
                "   ");

        var violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(v -> {
                    assertThat(v.getPropertyPath().toString()).isEqualTo("city");
                    assertThat(v.getMessage()).isEqualTo("City is required");
                });
    }

    @Test
    void createVehicle_nullCity_isInvalid() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                VehicleCategory.SEDAN,
                "Toyota",
                "Camry",
                2020,
                "ABC-123",
                null,
                TransmissionType.AUTO,
                FuelType.PETROL,
                5,
                null,
                null);

        var violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(v -> {
                    assertThat(v.getPropertyPath().toString()).isEqualTo("city");
                    assertThat(v.getMessage()).isEqualTo("City is required");
                });
    }

    @Test
    void updateVehicle_blankCity_isInvalid() {
        UpdateVehicleRequest request = new UpdateVehicleRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "   ");

        var violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(v -> {
                    assertThat(v.getPropertyPath().toString()).isEqualTo("city");
                    assertThat(v.getMessage()).isEqualTo("City must not be blank");
                });
    }

    @Test
    void updateVehicle_nullCity_isValid() {
        UpdateVehicleRequest request = new UpdateVehicleRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        var violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
