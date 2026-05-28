package com.rentflow.file.controller;

import com.rentflow.file.dto.SignedFileUrlResponse;
import com.rentflow.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
