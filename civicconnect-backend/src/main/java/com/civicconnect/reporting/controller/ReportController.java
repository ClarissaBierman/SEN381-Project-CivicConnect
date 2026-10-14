package com.civicconnect.reporting.controller;

import com.civicconnect.reporting.dto.ActivityReport;
import com.civicconnect.reporting.service.ReportService;
import com.civicconnect.requests.domain.ServiceRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** API-17: Management reports. Counts only (ASR-005). */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('MANAGEMENT')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** e.g. /api/v1/reports/activity?from=2026-10-01&to=2026-10-31&categoryId=4&status=NEW */
    @GetMapping("/activity")
    public ActivityReport activity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ServiceRequest.Status status) {
        return reportService.activity(from, to, categoryId, status);
    }
}
