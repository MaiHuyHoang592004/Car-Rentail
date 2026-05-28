package com.rentflow.file.controller;

import com.rentflow.file.dto.AddListingPhotoRequest;
import com.rentflow.file.dto.ListingPhotoResponse;
import com.rentflow.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/host/listings")
@RequiredArgsConstructor
public class HostListingPhotoController {

    private final FileService fileService;

    @PostMapping("/{id}/photos")
    public ResponseEntity<ListingPhotoResponse> addListingPhoto(
            @PathVariable("id") UUID listingId,
            @Valid @RequestBody AddListingPhotoRequest request) {
        ListingPhotoResponse response = fileService.addListingPhoto(listingId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
