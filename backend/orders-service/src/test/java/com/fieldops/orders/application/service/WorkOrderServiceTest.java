package com.fieldops.orders.application.service;

import com.fieldops.orders.application.dto.CreateWorkOrderRequest;
import com.fieldops.orders.application.dto.WorkOrderResponse;
import com.fieldops.orders.domain.exception.ResourceNotFoundException;
import com.fieldops.orders.domain.model.Client;
import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.Priority;
import com.fieldops.orders.domain.model.WorkOrder;
import com.fieldops.orders.domain.model.WorkOrderStatusHistory;
import com.fieldops.orders.infrastructure.persistence.ClientRepository;
import com.fieldops.orders.infrastructure.persistence.WorkOrderRepository;
import com.fieldops.orders.infrastructure.persistence.WorkOrderStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private WorkOrderStatusHistoryRepository historyRepository;

    @Mock
    private WorkOrderCodeGenerator codeGenerator;

    private final WorkOrderMapper mapper = new WorkOrderMapper();
    private WorkOrderService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(workOrderRepository, clientRepository, historyRepository, codeGenerator, mapper);
    }

    @Test
    void createWorkOrderWithoutTechnicianStartsInDraft() {
        Client client = new Client();
        client.setId(1L);
        client.setBusinessName("Acme Corp");
        client.setTaxId("TAX-001");

        CreateWorkOrderRequest request = new CreateWorkOrderRequest(
                "Maintenance HVAC",
                "Periodic inspection",
                Priority.HIGH,
                1L,
                null,
                null
        );

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(codeGenerator.generateNextCode()).thenReturn("WO-2026-00001");
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder order = invocation.getArgument(0);
            order.setId(10L);
            return order;
        });

        WorkOrderResponse response = service.createWorkOrder(request, 100L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.code()).isEqualTo("WO-2026-00001");
        assertThat(response.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(response.priority()).isEqualTo(Priority.HIGH);
        assertThat(response.assignedTechnicianId()).isNull();

        ArgumentCaptor<WorkOrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(WorkOrderStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo(OrderStatus.DRAFT);
    }

    @Test
    void createWorkOrderWithTechnicianStartsInAssigned() {
        Client client = new Client();
        client.setId(1L);
        client.setBusinessName("Acme Corp");

        CreateWorkOrderRequest request = new CreateWorkOrderRequest(
                "Emergency Repair",
                "Water leak",
                Priority.CRITICAL,
                1L,
                55L,
                LocalDateTime.now().plusDays(1)
        );

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(codeGenerator.generateNextCode()).thenReturn("WO-2026-00002");
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder order = invocation.getArgument(0);
            order.setId(20L);
            return order;
        });

        WorkOrderResponse response = service.createWorkOrder(request, 100L);

        assertThat(response.status()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(response.assignedTechnicianId()).isEqualTo(55L);
    }

    @Test
    void createWorkOrderWithNonexistentClientThrowsException() {
        CreateWorkOrderRequest request = new CreateWorkOrderRequest(
                "Maintenance HVAC",
                "Periodic inspection",
                Priority.LOW,
                999L,
                null,
                null
        );

        when(clientRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createWorkOrder(request, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}