package com.rentflow.file.controller;

import com.rentflow.file.dto.AddListingPhotoRequest;
import com.rentflow.file.dto.CreatePhotoUploadIntentRequest;
import com.rentflow.file.dto.FileUploadIntentResponse;
import com.rentflow.file.dto.ListingPhotoResponse;
import com.rentflow.file.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HostListingPhotoControllerTest {

    private MockMvc mockMvc;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = mock(FileService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new HostListingPhotoController(fileService)).build();
    }

    @Test
    void addListingPhotoReturns201() throws Exception {
        UUID listingId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(fileService.addListingPhoto(eq(listingId), eq(new AddListingPhotoRequest(
                fileId, true, null))))
                .thenReturn(new ListingPhotoResponse(
                        photoId, listingId, fileId, true, 0, "PRIVATE", "https://files.local/url",
                        Instant.parse("2026-05-29T00:10:00Z")));

        mockMvc.perform(post("/api/v1/host/listings/{id}/photos", listingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileId":"%s",
                                  "primary":true
                                }
                                """.formatted(fileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(photoId.toString()))
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    @Test
    void createListingPhotoUploadIntentReturns200() throws Exception {
        UUID listingId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(fileService.createListingPhotoUploadIntent(eq(listingId), eq(new CreatePhotoUploadIntentRequest(
                "image/jpeg", 2048L, "abc123"))))
                .thenReturn(new FileUploadIntentResponse(
                        fileId,
                        "rentflow-listing-photos",
                        "listings/" + listingId + "/" + fileId,
                        "https://upload.local/file-1",
                        Instant.parse("2026-05-29T00:10:00Z")));

        mockMvc.perform(post("/api/v1/host/listings/{id}/photos/upload-intents", listingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentType":"image/jpeg",
                                  "sizeBytes":2048,
                                  "checksum":"abc123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.bucket").value("rentflow-listing-photos"))
                .andExpect(jsonPath("$.uploadUrl").value("https://upload.local/file-1"));
    }
}
