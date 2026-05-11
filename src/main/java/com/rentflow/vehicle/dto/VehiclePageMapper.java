package com.rentflow.vehicle.dto;

import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class VehiclePageMapper {

    public Page<VehicleResponse> toResponsePage(Page<Vehicle> vehicles) {
        return vehicles.map(VehicleResponse::from);
    }
}
