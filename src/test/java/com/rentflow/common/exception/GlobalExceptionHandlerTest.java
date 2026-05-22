package com.rentflow.common.exception;

import com.rentflow.common.web.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new CorrelationIdHelper());
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void notFoundUsesCodeFromResourceException() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new VehicleNotFoundException("vehicle-id"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VEHICLE_NOT_FOUND");
    }

    @Test
    void optimisticLockReturnsConflictRetryError() {
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(Object.class, "listing-id"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(response.getBody().message()).contains("retry");
    }
}
