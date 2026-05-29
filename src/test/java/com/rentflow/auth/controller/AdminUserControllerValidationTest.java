package com.rentflow.auth.controller;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AdminUserControllerValidationTest {

    @Test
    void listUsersWithInvalidStatusThrowsValidationException() {
        AdminUserController controller = new AdminUserController(mock(UserService.class));

        assertThatThrownBy(() -> controller.listUsers("INVALID", null, PageRequest.of(0, 20)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid user status: INVALID");
    }

    @Test
    void listUsersWithInvalidRoleThrowsValidationException() {
        AdminUserController controller = new AdminUserController(mock(UserService.class));

        assertThatThrownBy(() -> controller.listUsers(null, "INVALID", PageRequest.of(0, 20)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid user role: INVALID");
    }
}
