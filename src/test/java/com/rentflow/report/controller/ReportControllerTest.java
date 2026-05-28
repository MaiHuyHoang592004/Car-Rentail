package com.rentflow.report.controller;

import com.rentflow.report.dto.EarningsReportResponse;
import com.rentflow.report.dto.RevenueReportResponse;
import com.rentflow.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private MockMvc mockMvc;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminReportController(reportService),
                new HostReportController(reportService))
                .build();
    }

    @Test
    void adminRevenueReturns200() throws Exception {
        when(reportService.revenue(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .thenReturn(new RevenueReportResponse(
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 31),
                        new BigDecimal("1000000.00"),
                        new BigDecimal("150000.00"),
                        new BigDecimal("850000.00"),
                        3));

        mockMvc.perform(get("/api/v1/admin/reports/revenue")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCaptured").value(1000000.00))
                .andExpect(jsonPath("$.platformFeeAmount").value(150000.00));
    }

    @Test
    void hostEarningsReturns200() throws Exception {
        UUID hostId = UUID.randomUUID();
        when(reportService.hostEarnings(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .thenReturn(new EarningsReportResponse(
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 31),
                        hostId,
                        new BigDecimal("2000000.00"),
                        new BigDecimal("300000.00"),
                        new BigDecimal("1700000.00"),
                        5));

        mockMvc.perform(get("/api/v1/host/reports/earnings")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostId").value(hostId.toString()))
                .andExpect(jsonPath("$.netEarnings").value(1700000.00));
    }
}
