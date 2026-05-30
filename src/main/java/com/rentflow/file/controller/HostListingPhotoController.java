package com.rentflow.file.controller;

import com.rentflow.file.dto.AddListingPhotoRequest;
import com.rentflow.file.dto.ListingPhotoResponse;
import com.rentflow.file.dto.UpdateListingPhotoRequest;
import com.rentflow.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/{id}/photos")
    public ResponseEntity<List<ListingPhotoResponse>> listListingPhotos(@PathVariable("id") UUID listingId) {
        return ResponseEntity.ok(fileService.listListingPhotos(listingId));
    }

    @PatchMapping("/{id}/photos/{photoId}")
    public ResponseEntity<ListingPhotoResponse> updateListingPhoto(
            @PathVariable("id") UUID listingId,
            @PathVariable UUID photoId,
            @Valid @RequestBody UpdateListingPhotoRequest request) {
        return ResponseEntity.ok(fileService.updateListingPhoto(listingId, photoId, request));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> deleteListingPhoto(
            @PathVariable("id") UUID listingId,
            @PathVariable UUID photoId) {
        fileService.deleteListingPhoto(listingId, photoId);
        return ResponseEntity.noContent().build();
    }
}
