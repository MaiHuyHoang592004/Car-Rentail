package com.rentflow.file.controller;

import com.rentflow.file.dto.SignedFileUrlResponse;
import com.rentflow.file.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest {

    private MockMvc mockMvc;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = mock(FileService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService)).build();
    }

    @Test
    void getSignedUrlReturns200() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.getSignedUrl(fileId))
                .thenReturn(new SignedFileUrlResponse(
                        fileId,
                        "PUBLIC",
                        "https://files.local/signed",
                        Instant.parse("2026-05-29T00:20:00Z")));

        mockMvc.perform(get("/api/v1/files/{fileId}/signed-url", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.signedUrl").value("https://files.local/signed"));
    }
}
