package com.rentflow.dispute.controller;

import com.rentflow.dispute.dto.CreateDisputeRequest;
import com.rentflow.dispute.dto.DisputeResponse;
import com.rentflow.dispute.service.DisputeService;
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
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping("/{id}/dispute")
    public ResponseEntity<DisputeResponse> createDispute(
            @PathVariable("id") UUID bookingId,
            @Valid @RequestBody CreateDisputeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disputeService.createDispute(bookingId, request));
    }
}
