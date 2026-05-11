package com.rentflow.vehicle.service;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.vehicle.entity.VehicleStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class VehicleStateMachine {

    private static final Map<VehicleStatus, Set<VehicleStatus>> ALLOWED_TRANSITIONS = Map.of(
        VehicleStatus.DRAFT,      Set.of(VehicleStatus.ACTIVE),
        VehicleStatus.ACTIVE,     Set.of(VehicleStatus.MAINTENANCE, VehicleStatus.SUSPENDED, VehicleStatus.ARCHIVED),
        VehicleStatus.MAINTENANCE, Set.of(VehicleStatus.ACTIVE, VehicleStatus.SUSPENDED),
        VehicleStatus.SUSPENDED, Set.of(VehicleStatus.ACTIVE, VehicleStatus.MAINTENANCE),
        VehicleStatus.ARCHIVED,   Set.of()
    );

    public void validateTransition(VehicleStatus from, VehicleStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                "Cannot transition vehicle from " + from + " to " + to);
        }
    }

    public boolean canTransition(VehicleStatus from, VehicleStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<VehicleStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
