package com.rentflow.vehicle.controller;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.vehicle.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class VehicleControllerValidationTest {

    @Test
    void listVehiclesWithInvalidStatusThrowsValidationException() {
        VehicleController controller = new VehicleController(mock(VehicleService.class));

        assertThatThrownBy(() -> controller.listVehicles(
                "INVALID",
                0,
                20,
                Sort.by(Sort.Direction.DESC, "createdAt"),
                null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid vehicle status: INVALID");
    }
}
