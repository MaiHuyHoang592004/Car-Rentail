package com.rentflow.vehicle;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.service.VehicleStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VehicleStateMachine")
class VehicleStateMachineTest {

    private final VehicleStateMachine stateMachine = new VehicleStateMachine();

    @Nested
    @DisplayName("valid transitions")
    class ValidTransitions {

        @CsvSource({
            "DRAFT,      ACTIVE",
            "ACTIVE,     MAINTENANCE",
            "ACTIVE,     SUSPENDED",
            "ACTIVE,     ARCHIVED",
            "MAINTENANCE, ACTIVE",
            "MAINTENANCE, SUSPENDED",
            "SUSPENDED,  ACTIVE",
            "SUSPENDED,  MAINTENANCE"
        })
        @ParameterizedTest(name = "{0} -> {1}")
        void canTransition_returnsTrue(VehicleStatus from, VehicleStatus to) {
            assertThat(stateMachine.canTransition(from, to)).isTrue();
        }

        @CsvSource({
            "DRAFT,      ACTIVE",
            "ACTIVE,     MAINTENANCE",
            "MAINTENANCE, ACTIVE"
        })
        @ParameterizedTest(name = "{0} -> {1} throws nothing")
        void validTransition_doesNotThrow(VehicleStatus from, VehicleStatus to) {
            stateMachine.validateTransition(from, to);
        }
    }

    @Nested
    @DisplayName("invalid transitions")
    class InvalidTransitions {

        @CsvSource({
            "DRAFT,      ARCHIVED",
            "DRAFT,      SUSPENDED",
            "DRAFT,      MAINTENANCE",
            "ARCHIVED,   ACTIVE",
            "ARCHIVED,   MAINTENANCE",
            "ARCHIVED,   SUSPENDED",
            "ARCHIVED,   DRAFT",
            "ACTIVE,     DRAFT"
        })
        @ParameterizedTest(name = "{0} -> {1}")
        void canTransition_returnsFalse(VehicleStatus from, VehicleStatus to) {
            assertThat(stateMachine.canTransition(from, to)).isFalse();
        }

        @CsvSource({
            "DRAFT,      ARCHIVED",
            "DRAFT,      MAINTENANCE",
            "ARCHIVED,   ACTIVE",
            "ACTIVE,     DRAFT"
        })
        @ParameterizedTest(name = "{0} -> {1} throws BusinessRuleException")
        void invalidTransition_throwsException(VehicleStatus from, VehicleStatus to) {
            assertThatThrownBy(() -> stateMachine.validateTransition(from, to))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_STATUS_TRANSITION");
        }

        @Test
        @DisplayName("null from returns false")
        void nullFrom_returnsFalse() {
            assertThat(stateMachine.canTransition(null, VehicleStatus.ACTIVE)).isFalse();
        }

        @Test
        @DisplayName("null to returns false")
        void nullTo_returnsFalse() {
            assertThat(stateMachine.canTransition(VehicleStatus.ACTIVE, null)).isFalse();
        }
    }
}
