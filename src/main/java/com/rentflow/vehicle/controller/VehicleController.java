package com.rentflow.vehicle.controller;

import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.security.UserPrincipal;
import com.rentflow.vehicle.dto.CreateVehicleRequest;
import com.rentflow.vehicle.dto.UpdateVehicleRequest;
import com.rentflow.vehicle.dto.VehicleArchivePreviewResponse;
import com.rentflow.vehicle.dto.VehicleResponse;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/host/vehicles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOST')")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    public ResponseEntity<VehicleResponse> createVehicle(
            @Valid @RequestBody CreateVehicleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        var response = vehicleService.createVehicle(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<VehicleResponse>> listVehicles(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Sort sort,
            @AuthenticationPrincipal UserPrincipal principal) {

        Pageable pageable = PageableValidation.of(page, size, sort);

        VehicleStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = VehicleStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Invalid vehicle status: " + status);
            }
        }

        Page<VehicleResponse> vehicles = vehicleService.listVehicles(
                principal.getUserId(), statusEnum, pageable);
        return ResponseEntity.ok(PageResponse.from(vehicles));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> getVehicle(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        var response = vehicleService.getVehicle(id, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/archive-preview")
    public ResponseEntity<VehicleArchivePreviewResponse> getArchivePreview(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        var response = vehicleService.getArchivePreview(id, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<VehicleResponse> updateVehicle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVehicleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        var response = vehicleService.updateVehicle(id, principal.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archiveVehicle(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        vehicleService.archiveVehicle(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
