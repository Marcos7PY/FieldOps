package com.fieldops.analytics.api.controller;

import com.fieldops.analytics.api.dto.DailyMetricsResponse;
import com.fieldops.analytics.api.dto.RebuildStatusResponse;
import com.fieldops.analytics.api.dto.TechnicianMetricsResponse;
import com.fieldops.analytics.application.service.AnalyticsQueryService;
import com.fieldops.analytics.application.service.ProjectionRebuildService;
import com.fieldops.analytics.domain.exception.RebuildAlreadyInProgressException;
import com.fieldops.analytics.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@Import(SecurityConfig.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsQueryService queryService;

    @MockitoBean
    private ProjectionRebuildService rebuildService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/metrics/daily"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TECHNICIAN")
    void shouldReturn403WhenTechnicianAccessesDailyMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/metrics/daily"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void shouldReturnDailyMetricsForSupervisor() throws Exception {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 13);
        DailyMetricsResponse response = new DailyMetricsResponse(from, to, 10L, BigDecimal.valueOf(65.5), List.of());

        when(queryService.getDailyMetrics(any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/analytics/metrics/daily")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(10))
                .andExpect(jsonPath("$.overallAvgDurationMinutes").value(65.5));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void shouldReturnTechnicianMetricsForSupervisor() throws Exception {
        TechnicianMetricsResponse response = new TechnicianMetricsResponse(List.of());

        when(queryService.getTechnicianMetrics()).thenReturn(response);

        mockMvc.perform(get("/api/v1/analytics/metrics/technicians"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians").isArray());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void shouldReturn202AcceptedWhenRebuildingProjections() throws Exception {
        when(rebuildService.rebuildProjectionAsync()).thenReturn(CompletableFuture.completedFuture(null));

        mockMvc.perform(post("/api/v1/analytics/projections/rebuild"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(rebuildService).rebuildProjectionAsync();
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    @DisplayName("F3-T04: Devuelve 409 Conflict si ya hay una reconstrucción en curso")
    void shouldReturn409WhenRebuildAlreadyInProgress() throws Exception {
        when(rebuildService.rebuildProjectionAsync())
                .thenThrow(new RebuildAlreadyInProgressException("Ya hay una reconstrucción de proyección en curso"));

        mockMvc.perform(post("/api/v1/analytics/projections/rebuild"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya hay una reconstrucción de proyección en curso"));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    @DisplayName("F3-T04: GET /projections/rebuild/status devuelve el estado de reconstrucción")
    void shouldReturnRebuildStatus() throws Exception {
        LocalDateTime timestamp = LocalDateTime.of(2026, 9, 13, 12, 0);
        when(rebuildService.getRebuildStatus()).thenReturn(new RebuildStatusResponse(false, timestamp));

        mockMvc.perform(get("/api/v1/analytics/projections/rebuild/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inProgress").value(false))
                .andExpect(jsonPath("$.lastRebuiltAt").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "TECHNICIAN")
    void shouldReturn403WhenTechnicianRebuildsProjections() throws Exception {
        mockMvc.perform(post("/api/v1/analytics/projections/rebuild"))
                .andExpect(status().isForbidden());
    }
}
