package com.rentflow.file.controller;

import com.rentflow.file.dto.AddVehiclePhotoRequest;
import com.rentflow.file.dto.VehiclePhotoResponse;
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

class HostVehiclePhotoControllerTest {

    private MockMvc mockMvc;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = mock(FileService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new HostVehiclePhotoController(fileService)).build();
    }

    @Test
    void addVehiclePhotoReturns201() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(fileService.addVehiclePhoto(eq(vehicleId), eq(new AddVehiclePhotoRequest(
                "bucket-a", "vehicles/key.jpg", "image/jpeg", 2048L, "abc123", true))))
                .thenReturn(new VehiclePhotoResponse(
                        photoId, vehicleId, fileId, true, 0, "PRIVATE", "https://files.local/url",
                        Instant.parse("2026-05-29T00:10:00Z")));

        mockMvc.perform(post("/api/v1/host/vehicles/{id}/photos", vehicleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bucket":"bucket-a",
                                  "objectKey":"vehicles/key.jpg",
                                  "contentType":"image/jpeg",
                                  "sizeBytes":2048,
                                  "checksum":"abc123",
                                  "primary":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(photoId.toString()))
                .andExpect(jsonPath("$.vehicleId").value(vehicleId.toString()))
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }
}
