package com.fieldops.analytics.api.controller;

import com.fieldops.analytics.api.dto.DailyMetricsResponse;
import com.fieldops.analytics.api.dto.TechnicianMetricsResponse;
import com.fieldops.analytics.application.service.AnalyticsQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping({"/api/v1/analytics", "/analytics"})
public class AnalyticsController {

    private final AnalyticsQueryService queryService;

    public AnalyticsController(AnalyticsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/metrics/daily")
    @PreAuthorize("hasRole('ROLE_SUPERVISOR')")
    public ResponseEntity<DailyMetricsResponse> getDailyMetrics(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(queryService.getDailyMetrics(from, to));
    }

    @GetMapping("/metrics/technicians")
    @PreAuthorize("hasRole('ROLE_SUPERVISOR')")
    public ResponseEntity<TechnicianMetricsResponse> getTechnicianMetrics() {
        return ResponseEntity.ok(queryService.getTechnicianMetrics());
    }
}
