package com.rentflow.file.controller;

import com.rentflow.file.dto.AddVehiclePhotoRequest;
import com.rentflow.file.dto.CreatePhotoUploadIntentRequest;
import com.rentflow.file.dto.FileUploadIntentResponse;
import com.rentflow.file.dto.UpdateVehiclePhotoRequest;
import com.rentflow.file.dto.VehiclePhotoResponse;
import com.rentflow.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/host/vehicles/{vehicleId}/photos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOST')")
public class HostVehiclePhotoController {

    private final FileService fileService;

    @PostMapping("/upload-intents")
    public ResponseEntity<FileUploadIntentResponse> createVehiclePhotoUploadIntent(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody CreatePhotoUploadIntentRequest request) {
        return ResponseEntity.ok(fileService.createVehiclePhotoUploadIntent(vehicleId, request));
    }

    @PostMapping
    public ResponseEntity<VehiclePhotoResponse> addVehiclePhoto(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody AddVehiclePhotoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileService.addVehiclePhoto(vehicleId, request));
    }

    @GetMapping
    public ResponseEntity<List<VehiclePhotoResponse>> listVehiclePhotos(@PathVariable UUID vehicleId) {
        return ResponseEntity.ok(fileService.listVehiclePhotos(vehicleId));
    }

    @PatchMapping("/{photoId}")
    public ResponseEntity<VehiclePhotoResponse> updateVehiclePhoto(
            @PathVariable UUID vehicleId,
            @PathVariable UUID photoId,
            @Valid @RequestBody UpdateVehiclePhotoRequest request) {
        return ResponseEntity.ok(fileService.updateVehiclePhoto(vehicleId, photoId, request));
    }

    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deleteVehiclePhoto(
            @PathVariable UUID vehicleId,
            @PathVariable UUID photoId) {
        fileService.deleteVehiclePhoto(vehicleId, photoId);
        return ResponseEntity.noContent().build();
    }
}
