package com.rentflow.file.controller;

import com.rentflow.file.dto.CreateDisputeAttachmentUploadRequest;
import com.rentflow.file.dto.FileUploadIntentResponse;
import com.rentflow.file.dto.SignedFileUrlResponse;
import com.rentflow.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping("/{fileId}/signed-url")
    public ResponseEntity<SignedFileUrlResponse> getSignedUrl(@PathVariable UUID fileId) {
        return ResponseEntity.ok(fileService.getSignedUrl(fileId));
    }

    @PostMapping("/dispute-attachments/upload-intents")
    public ResponseEntity<FileUploadIntentResponse> createDisputeAttachmentUploadIntent(
            @Valid @RequestBody CreateDisputeAttachmentUploadRequest request) {
        return ResponseEntity.ok(fileService.createDisputeAttachmentUploadIntent(request));
    }

    @PostMapping("/{fileId}/finalize")
    public ResponseEntity<SignedFileUrlResponse> finalizeUpload(@PathVariable UUID fileId) {
        return ResponseEntity.ok(fileService.finalizeUpload(fileId));
    }
}
