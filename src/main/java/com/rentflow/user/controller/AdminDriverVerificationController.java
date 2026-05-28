package com.rentflow.user.controller;

import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.web.PageResponse;
import com.rentflow.user.dto.DriverVerificationResponse;
import com.rentflow.user.dto.ReviewDriverVerificationRequest;
import com.rentflow.user.entity.DriverVerificationStatus;
import com.rentflow.user.service.DriverVerificationService;
import com.rentflow.common.exception.ValidationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/driver-verifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDriverVerificationController {

    private final DriverVerificationService driverVerificationService;
    private final SecurityContext securityContext;

    @GetMapping
    public ResponseEntity<PageResponse<DriverVerificationResponse>> listDriverVerifications(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        DriverVerificationStatus statusFilter = parseStatus(status);
        return ResponseEntity.ok(PageResponse.from(driverVerificationService.list(statusFilter, pageable)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<DriverVerificationResponse> approve(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewDriverVerificationRequest request) {
        DriverVerificationResponse response = driverVerificationService.approve(securityContext.currentUserId(), id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<DriverVerificationResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewDriverVerificationRequest request) {
        DriverVerificationResponse response = driverVerificationService.reject(securityContext.currentUserId(), id, request);
        return ResponseEntity.ok(response);
    }

    private DriverVerificationStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return DriverVerificationStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid driver verification status: " + rawStatus);
        }
    }
}
