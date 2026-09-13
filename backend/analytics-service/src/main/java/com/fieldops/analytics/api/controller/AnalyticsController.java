package com.fieldops.analytics.api.controller;

import com.fieldops.analytics.api.dto.DailyMetricsResponse;
import com.fieldops.analytics.api.dto.RebuildProjectionResponse;
import com.fieldops.analytics.api.dto.TechnicianMetricsResponse;
import com.fieldops.analytics.application.service.AnalyticsQueryService;
import com.fieldops.analytics.application.service.ProjectionRebuildService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping({"/api/v1/analytics", "/analytics"})
public class AnalyticsController {

    private final AnalyticsQueryService queryService;
    private final ProjectionRebuildService rebuildService;

    public AnalyticsController(AnalyticsQueryService queryService, ProjectionRebuildService rebuildService) {
        this.queryService = queryService;
        this.rebuildService = rebuildService;
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

    @PostMapping("/projections/rebuild")
    @PreAuthorize("hasRole('ROLE_SUPERVISOR')")
    public ResponseEntity<RebuildProjectionResponse> rebuildProjections() {
        rebuildService.rebuildProjectionAsync();
        return ResponseEntity.accepted().body(new RebuildProjectionResponse(
                "ACCEPTED",
                "Reconstrucción de la proyección iniciada en segundo plano",
                LocalDateTime.now()
        ));
    }
}
