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

        AssignWorkOrderRequest assignReq = new AssignWorkOrderRequest(100L, LocalDateTime.now().plusDays(1));
        MvcResult ar = mockMvc.perform(patch("/api/v1/work-orders/{id}/assign", orderId)
                        .with(supervisor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"" + version + "\"")
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andReturn();
        version = objectMapper.readTree(ar.getResponse().getContentAsString()).get("version").asLong();

        mockMvc.perform(get("/api/v1/work-orders/{id}", orderId)
                        .with(technician(200L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));

        ChangeStatusRequest startReq = new ChangeStatusRequest(OrderStatus.IN_PROGRESS, "Attacking order");
        mockMvc.perform(patch("/api/v1/work-orders/{id}/status", orderId)
                        .with(technician(200L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"" + version + "\"")
                        .content(objectMapper.writeValueAsString(startReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));

        mockMvc.perform(get("/api/v1/work-orders/{id}", orderId)
                        .with(technician(100L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.assignedTechnicianId").value(100));

        ChangeStatusRequest cancelReq = new ChangeStatusRequest(OrderStatus.CANCELLED, "Technician cancelling");
        mockMvc.perform(patch("/api/v1/work-orders/{id}/status", orderId)
                        .with(technician(100L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"" + version + "\"")
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business Rule Violation"));
    }

    @Test
    void f2t02_jwtWithoutUserIdClaim_shouldReturnForbidden() throws Exception {
        CreateWorkOrderRequest orderReq = new CreateWorkOrderRequest(
                "Order with invalid jwt",
                "Description",
                Priority.HIGH,
                1L,
                null,
                null
        );

        // JWT con rol supervisor pero SIN userId claim
        RequestPostProcessor supervisorWithoutUserId = jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
                .jwt(j -> j.claim("roles", List.of("ROLE_SUPERVISOR")));

        mockMvc.perform(post("/api/v1/work-orders")
                        .with(supervisorWithoutUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("El token no contiene un identificador de usuario válido"));
    }

    @Test
    void f2t04_assignWithoutIfMatch_shouldFail() throws Exception {
        AssignWorkOrderRequest assignReq = new AssignWorkOrderRequest(10L, LocalDateTime.now().plusDays(1));

        mockMvc.perform(patch("/api/v1/work-orders/1/assign")
                        .with(supervisor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void f2t04_assignWithInvalidIfMatch_shouldReturnConflict() throws Exception {
        AssignWorkOrderRequest assignReq = new AssignWorkOrderRequest(10L, LocalDateTime.now().plusDays(1));

        mockMvc.perform(patch("/api/v1/work-orders/1/assign")
                        .with(supervisor(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"abc\"")
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La cabecera If-Match debe contener una versión numérica válida"));
    }

    @Test
    void f2t10_uploadsHeadersAndAuthentication() throws Exception {
        // Sin token -> 401
        mockMvc.perform(get("/uploads/test.png"))
                .andExpect(status().isUnauthorized());

        // Con token -> cabeceras de seguridad requeridas por F2-T10
        mockMvc.perform(get("/uploads/test.png")
                        .with(supervisor(1L)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "private, max-age=3600"));
    }
}
