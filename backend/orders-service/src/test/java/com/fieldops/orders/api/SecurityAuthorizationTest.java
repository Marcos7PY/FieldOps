package com.fieldops.orders.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.orders.AbstractIntegrationTest;
import com.fieldops.orders.application.dto.AssignWorkOrderRequest;
import com.fieldops.orders.application.dto.ChangeStatusRequest;
import com.fieldops.orders.application.dto.CreateClientRequest;
import com.fieldops.orders.application.dto.CreateWorkOrderRequest;
import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.Priority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private RequestPostProcessor supervisor(Long userId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
                .jwt(j -> j.claim("userId", userId).claim("roles", List.of("ROLE_SUPERVISOR")));
    }

    private RequestPostProcessor technician(Long technicianId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))
                .jwt(j -> j.claim("userId", technicianId).claim("roles", List.of("ROLE_TECHNICIAN")));
    }

    @Test
    void shouldReturnUnauthorizedWhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldDenyTechnicianFromCreatingWorkOrder() throws Exception {
        CreateWorkOrderRequest orderReq = new CreateWorkOrderRequest(
                "Technician unauthorized order",
                "Description",
                Priority.LOW,
                1L,
                null,
                null
        );

        mockMvc.perform(post("/api/v1/work-orders")
                        .with(technician(10L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyTechnicianFromCreatingClient() throws Exception {
        CreateClientRequest clientReq = new CreateClientRequest(
                "Unauthorized Client",
                "TAX-UNAUTH-001",
                null, null, null, null
        );

        mockMvc.perform(post("/api/v1/clients")
                        .with(technician(10L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyTechnicianFromAssigningWorkOrder() throws Exception {
        AssignWorkOrderRequest assignReq = new AssignWorkOrderRequest(
                10L,
                LocalDateTime.now().plusDays(1)
        );

        mockMvc.perform(patch("/api/v1/work-orders/1/assign")
                        .with(technician(10L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"0\"")
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldEnforceTechnicianOwnershipRuleInServiceLayer() throws Exception {
        // 1. Create client and work order as supervisor
        CreateClientRequest clientReq = new CreateClientRequest(
                "Ownership Test Client",
                "TAX-OWN-001",
                null, null, null, null
        );
        MvcResult cr = mockMvc.perform(post("/api/v1/clients")
                        .with(supervisor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientReq)))
                .andExpect(status().isCreated())
                .andReturn();
        long clientId = objectMapper.readTree(cr.getResponse().getContentAsString()).get("id").asLong();

        CreateWorkOrderRequest orderReq = new CreateWorkOrderRequest(
                "Ownership Order",
                "Assigned to technician 100",
                Priority.MEDIUM,
                clientId,
                null,
                null
        );
        MvcResult or = mockMvc.perform(post("/api/v1/work-orders")
                        .with(supervisor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = objectMapper.readTree(or.getResponse().getContentAsString()).get("id").asLong();
        long version = objectMapper.readTree(or.getResponse().getContentAsString()).get("version").asLong();

        // 2. Assign to technician 100
        AssignWorkOrderRequest assignReq = new AssignWorkOrderRequest(100L, LocalDateTime.now().plusDays(1));
        MvcResult ar = mockMvc.perform(patch("/api/v1/work-orders/{id}/assign", orderId)
                        .with(supervisor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"" + version + "\"")
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andReturn();
        version = objectMapper.readTree(ar.getResponse().getContentAsString()).get("version").asLong();

        // 3. Technician 200 (not assigned) tries to view order -> 403 Forbidden
        mockMvc.perform(get("/api/v1/work-orders/{id}", orderId)
                        .with(technician(200L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));

        // 4. Technician 200 tries to update status -> 403 Forbidden
        ChangeStatusRequest startReq = new ChangeStatusRequest(OrderStatus.IN_PROGRESS, "Attacking order");
        mockMvc.perform(patch("/api/v1/work-orders/{id}/status", orderId)
                        .with(technician(200L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"" + version + "\"")
                        .content(objectMapper.writeValueAsString(startReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));

        // 5. Technician 100 (assigned) views order -> 200 OK
        mockMvc.perform(get("/api/v1/work-orders/{id}", orderId)
                        .with(technician(100L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.assignedTechnicianId").value(100));

        // 6. Technician 100 tries to cancel order -> 422 Unprocessable Entity (rule: only supervisors can cancel)
        ChangeStatusRequest cancelReq = new ChangeStatusRequest(OrderStatus.CANCELLED, "Technician cancelling");
        mockMvc.perform(patch("/api/v1/work-orders/{id}/status", orderId)
                        .with(technician(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"" + version + "\"")
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business Rule Violation"));
    }
}