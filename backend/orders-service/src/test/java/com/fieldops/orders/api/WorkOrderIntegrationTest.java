package com.fieldops.orders.api;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkOrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.fieldops.orders.infrastructure.persistence.OutboxEventRepository outboxEventRepository;

    private RequestPostProcessor supervisor() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
                .jwt(j -> j.claim("userId", 1L).claim("roles", List.of("ROLE_SUPERVISOR")));
    }

    private RequestPostProcessor technician(Long technicianId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))
                .jwt(j -> j.claim("userId", technicianId).claim("roles", List.of("ROLE_TECHNICIAN")));
    }

    @Test
    void completeOrderLifecycleFlow() throws Exception {
        CreateClientRequest clientReq = new CreateClientRequest(
                "Telecom Solutions Inc",
                "TAX-TEL-001",
                "Main Street 123",
                BigDecimal.valueOf(40.7128),
                BigDecimal.valueOf(-74.0060),
                "+1555123456"
        );

        MvcResult clientResult = mockMvc.perform(post("/api/v1/clients")
                        .with(supervisor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();

        JsonNode clientNode = objectMapper.readTree(clientResult.getResponse().getContentAsString());
        long clientId = clientNode.get("id").asLong();

        CreateWorkOrderRequest orderReq = new CreateWorkOrderRequest(
                "Install Fiber Router",
                "Deploy GPON equipment at client premises",
                Priority.HIGH,
                clientId,
                null,
                LocalDateTime.now().plusDays(2)
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/work-orders")
                        .with(supervisor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", 1)
                        .header("X-User-Role", "ROLE_SUPERVISOR")
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andReturn();

        JsonNode orderNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long orderId = orderNode.get("id").asLong();
        long version = orderNode.get("version").asLong();

        AssignWorkOrderRequest assignReq = new AssignWorkOrderRequest(
                42L,
                LocalDateTime.now().plusDays(1)
        );

        MvcResult assignResult = mockMvc.perform(patch("/api/v1/work-orders/{id}/assign", orderId)
                        .with(supervisor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", 1)
                        .header("X-User-Role", "ROLE_SUPERVISOR")
                        .header("If-Match", "\"" + version + "\"")
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedTechnicianId").value(42))
                .andReturn();

        JsonNode assignedNode = objectMapper.readTree(assignResult.getResponse().getContentAsString());
        version = assignedNode.get("version").asLong();

        ChangeStatusRequest startReq = new ChangeStatusRequest(OrderStatus.IN_PROGRESS, "Arrived on site");
        MvcResult startResult = mockMvc.perform(patch("/api/v1/work-orders/{id}/status", orderId)
                        .with(technician(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", 42)
                        .header("X-User-Role", "ROLE_TECHNICIAN")
                        .header("If-Match", "\"" + version + "\"")
                        .content(objectMapper.writeValueAsString(startReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andReturn();

        JsonNode startedNode = objectMapper.readTree(startResult.getResponse().getContentAsString());
        version = startedNode.get("version").asLong();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "installation.jpg",
                "image/jpeg",
                new byte[]{10, 20, 30, 40}
        );

        mockMvc.perform(multipart("/api/v1/work-orders/{id}/evidence", orderId)
                        .file(file)
                        .with(technician(42L))
                        .header("X-User-Id", 42)
                        .header("X-User-Role", "ROLE_TECHNICIAN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filePath").isNotEmpty())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));

        ChangeStatusRequest completeReq = new ChangeStatusRequest(OrderStatus.COMPLETED, "Equipment fully tested");
        mockMvc.perform(patch("/api/v1/work-orders/{id}/status", orderId)
                        .with(technician(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", 42)
                        .header("X-User-Role", "ROLE_TECHNICIAN")
                        .header("If-Match", "\"" + version + "\"")
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/work-orders/metrics")
                        .with(supervisor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").isNumber())
                .andExpect(jsonPath("$.ordersByStatus.COMPLETED").isNumber());

        java.util.List<com.fieldops.orders.domain.model.OutboxEvent> outboxEvents =
                outboxEventRepository.findByAggregateIdOrderByCreatedAtAsc(orderId);
        assertThat(outboxEvents).hasSize(4);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(outboxEvents.get(0).getPublishedAt()).isNull();
        assertThat(outboxEvents.get(1).getEventType()).isEqualTo("ORDER_ASSIGNED");
        assertThat(outboxEvents.get(2).getEventType()).isEqualTo("ORDER_STARTED");
        assertThat(outboxEvents.get(3).getEventType()).isEqualTo("ORDER_COMPLETED");
    }

    @Test
    void invalidStatusTransitionReturnsConflict() throws Exception {
        CreateClientRequest clientReq = new CreateClientRequest(
                "Client Beta",
                "TAX-BETA-001",
                null, null, null, null
        );
        MvcResult cr = mockMvc.perform(post("/api/v1/clients")
                        .with(supervisor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long clientId = objectMapper.readTree(cr.getResponse().getContentAsString()).get("id").asLong();

        CreateWorkOrderRequest orderReq = new CreateWorkOrderRequest(
                "Direct Complete Attempt",
                null,
                Priority.LOW,
                clientId,
                null,
                null
        );
        MvcResult or = mockMvc.perform(post("/api/v1/work-orders")
                        .with(supervisor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderId = objectMapper.readTree(or.getResponse().getContentAsString()).get("id").asLong();

        ChangeStatusRequest skipReq = new ChangeStatusRequest(OrderStatus.COMPLETED, "Trying to skip from DRAFT");
        mockMvc.perform(patch("/api/v1/work-orders/{id}/status", orderId)
                        .with(supervisor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"0\"")
                        .header("X-User-Role", "ROLE_SUPERVISOR")
                        .content(objectMapper.writeValueAsString(skipReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid Status Transition"));
    }

    @Test
    void optimisticLockVersionMismatchReturnsConflict() throws Exception {
        CreateClientRequest clientReq = new CreateClientRequest(
                "Client Gamma",
                "TAX-GAMMA-001",
                null, null, null, null
        );
        MvcResult cr = mockMvc.perform(post("/api/v1/clients")
                        .with(supervisor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long clientId = objectMapper.readTree(cr.getResponse().getContentAsString()).get("id").asLong();

        CreateWorkOrderRequest orderReq = new CreateWorkOrderRequest(
                "Version Conflict Test",
                null,
                Priority.MEDIUM,
                clientId,
                null,
                null
        );
        MvcResult or = mockMvc.perform(post("/api/v1/work-orders")
                        .with(supervisor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderId = objectMapper.readTree(or.getResponse().getContentAsString()).get("id").asLong();

        ChangeStatusRequest cancelReq = new ChangeStatusRequest(OrderStatus.CANCELLED, "Supervisor cancelling");
        mockMvc.perform(patch("/api/v1/work-orders/{id}/status", orderId)
                        .with(supervisor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"999\"")
                        .header("X-User-Role", "ROLE_SUPERVISOR")
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Version Conflict"));
    }
}