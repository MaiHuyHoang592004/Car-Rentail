package com.rentflow.report.controller;

import com.rentflow.report.dto.EarningsReportResponse;
import com.rentflow.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/host/reports")
@RequiredArgsConstructor
public class HostReportController {

    private final ReportService reportService;

    @GetMapping("/earnings")
    public ResponseEntity<EarningsReportResponse> earnings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.hostEarnings(from, to));
    }
}
