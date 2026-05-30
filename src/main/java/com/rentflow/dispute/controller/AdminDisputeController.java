package com.rentflow.dispute.controller;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.dispute.dto.AdminDisputeDetailResponse;
import com.rentflow.dispute.dto.DisputeResponse;
import com.rentflow.dispute.dto.ResolveDisputeRequest;
import com.rentflow.dispute.entity.DisputeStatus;
import com.rentflow.dispute.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/disputes")
@RequiredArgsConstructor
public class AdminDisputeController {

    private final DisputeService disputeService;

    @GetMapping
    public ResponseEntity<PageResponse<DisputeResponse>> listDisputes(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        DisputeStatus statusFilter = parseStatus(status);
        return ResponseEntity.ok(disputeService.listDisputes(statusFilter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminDisputeDetailResponse> getDispute(@PathVariable("id") UUID disputeId) {
        return ResponseEntity.ok(disputeService.getAdminDisputeDetail(disputeId));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<DisputeResponse> resolveDispute(
            @PathVariable("id") UUID disputeId,
            @Valid @RequestBody ResolveDisputeRequest request) {
        return ResponseEntity.ok(disputeService.resolveDispute(disputeId, request));
    }

    private DisputeStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DisputeStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid dispute status: " + status);
        }
    }
}
