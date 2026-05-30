package com.rentflow.auth.controller;

import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.PageResponse;
import com.rentflow.booking.service.BookingSummaryResponse;
import com.rentflow.user.dto.UserStatusUpdateRequest;
import com.rentflow.user.dto.UserSummaryResponse;
import com.rentflow.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<PageResponse<UserSummaryResponse>> listUsers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        UserStatus statusEnum = parseUserStatus(status);
        Role roleEnum = parseRole(role);

        Page<UserSummaryResponse> page = userService.listUsers(statusEnum, roleEnum, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<UserSummaryResponse> suspendUser(
            @PathVariable("id") UUID userId,
            @Valid @RequestBody(required = false) UserStatusUpdateRequest request) {
        return ResponseEntity.ok(userService.suspendUser(userId));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<UserSummaryResponse> reactivateUser(@PathVariable("id") UUID userId) {
        return ResponseEntity.ok(userService.reactivateUser(userId));
    }

    @GetMapping("/{id}/bookings")
    public ResponseEntity<PageResponse<BookingSummaryResponse>> listUserBookings(
            @PathVariable("id") UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(userService.listUserBookings(userId, pageable)));
    }

    private UserStatus parseUserStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid user status: " + status);
        }
    }

    private Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid user role: " + role);
        }
    }
}
