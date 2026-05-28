package com.rentflow.dispute.controller;

import com.rentflow.common.web.PageResponse;
import com.rentflow.dispute.dto.CreateDisputeRequest;
import com.rentflow.dispute.dto.DisputeResponse;
import com.rentflow.dispute.dto.ResolveDisputeRequest;
import com.rentflow.dispute.entity.DisputeStatus;
import com.rentflow.dispute.service.DisputeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DisputeControllerTest {

    private MockMvc mockMvc;
    private DisputeService disputeService;

    @BeforeEach
    void setUp() {
        disputeService = mock(DisputeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new DisputeController(disputeService),
                new AdminDisputeController(disputeService))
                .build();
    }

    @Test
    void createDisputeReturns201() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        when(disputeService.createDispute(eq(bookingId), eq(new CreateDisputeRequest("Issue"))))
                .thenReturn(new DisputeResponse(
                        disputeId,
                        bookingId,
                        UUID.randomUUID(),
                        DisputeStatus.OPEN,
                        "Issue",
                        null,
                        null,
                        null,
                        Instant.parse("2026-05-29T00:00:00Z")));

        mockMvc.perform(post("/api/v1/bookings/{id}/dispute", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Issue"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(disputeId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void adminResolveAndListEndpointsReturn200() throws Exception {
        UUID disputeId = UUID.randomUUID();
        DisputeResponse resolved = new DisputeResponse(
                disputeId, UUID.randomUUID(), UUID.randomUUID(), DisputeStatus.RESOLVED,
                "Issue", "Refund approved", UUID.randomUUID(), Instant.parse("2026-05-29T00:10:00Z"),
                Instant.parse("2026-05-29T00:00:00Z"));
        when(disputeService.resolveDispute(eq(disputeId), eq(new ResolveDisputeRequest("Refund approved"))))
                .thenReturn(resolved);
        when(disputeService.listDisputes(eq(DisputeStatus.OPEN), any()))
                .thenReturn(new PageResponse<>(List.of(
                        new DisputeResponse(
                                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DisputeStatus.OPEN,
                                "Issue", null, null, null, Instant.parse("2026-05-29T00:00:00Z"))),
                        0, 20, 1, 1));

        mockMvc.perform(post("/api/v1/admin/disputes/{id}/resolve", disputeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionNote":"Refund approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(get("/api/v1/admin/disputes")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));
    }
}
