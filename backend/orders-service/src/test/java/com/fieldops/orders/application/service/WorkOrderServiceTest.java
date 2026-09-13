package com.fieldops.orders.application.service;

import com.fieldops.orders.application.dto.AssignWorkOrderRequest;
import com.fieldops.orders.application.dto.ChangeStatusRequest;
import com.fieldops.orders.application.dto.CreateWorkOrderRequest;
import com.fieldops.orders.application.dto.WorkOrderResponse;
import com.fieldops.orders.domain.exception.AccessDeniedException;
import com.fieldops.orders.domain.exception.BusinessRuleViolationException;
import com.fieldops.orders.domain.exception.InvalidStatusTransitionException;
import com.fieldops.orders.domain.exception.ResourceNotFoundException;
import com.fieldops.orders.domain.exception.VersionConflictException;
import com.fieldops.orders.domain.model.Client;
import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.Priority;
import com.fieldops.orders.domain.model.WorkOrder;
import com.fieldops.orders.domain.model.WorkOrderStatusHistory;
import com.fieldops.orders.infrastructure.persistence.ClientRepository;
import com.fieldops.orders.infrastructure.persistence.WorkOrderEvidenceRepository;
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
    private WorkOrderEvidenceRepository evidenceRepository;

    @Mock
    private WorkOrderStatusHistoryRepository historyRepository;

    @Mock
    private WorkOrderCodeGenerator codeGenerator;

    private final WorkOrderMapper mapper = new WorkOrderMapper();
    private WorkOrderService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(
                workOrderRepository,
                clientRepository,
                evidenceRepository,
                historyRepository,
                codeGenerator,
                mapper
        );
    }

    private WorkOrder createSampleOrder(Long id, OrderStatus status, Long technicianId, Long version) {
        Client client = new Client();
        client.setId(1L);
        client.setBusinessName("Acme Corp");

        WorkOrder order = new WorkOrder();
        order.setId(id);
        order.setCode("WO-2026-00001");
        order.setTitle("Sample Order");
        order.setStatus(status);
        order.setPriority(Priority.MEDIUM);
        order.setClient(client);
        order.setAssignedTechnicianId(technicianId);
        order.setCreatedBy(1L);
        order.setCreatedAt(LocalDateTime.now());
        order.setVersion(version);
        return order;
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

    @Test
    void assignWorkOrderTransitionsFromDraftToAssigned() {
        WorkOrder order = createSampleOrder(1L, OrderStatus.DRAFT, null, 0L);
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        AssignWorkOrderRequest request = new AssignWorkOrderRequest(42L, LocalDateTime.now().plusHours(4));
        WorkOrderResponse response = service.assignWorkOrder(1L, request, 99L, 0L);

        assertThat(response.status()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(response.assignedTechnicianId()).isEqualTo(42L);
        assertThat(order.getStatusHistory()).isNotEmpty();
    }

    @Test
    void assignWorkOrderWithVersionConflictThrowsException() {
        WorkOrder order = createSampleOrder(1L, OrderStatus.DRAFT, null, 1L);
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        AssignWorkOrderRequest request = new AssignWorkOrderRequest(42L, null);

        assertThatThrownBy(() -> service.assignWorkOrder(1L, request, 99L, 0L))
                .isInstanceOf(VersionConflictException.class);
    }

    @Test
    void changeStatusAssignedToInProgressSetsStartedAt() {
        WorkOrder order = createSampleOrder(2L, OrderStatus.ASSIGNED, 42L, 0L);
        when(workOrderRepository.findById(2L)).thenReturn(Optional.of(order));

        ChangeStatusRequest request = new ChangeStatusRequest(OrderStatus.IN_PROGRESS, "Started on site");
        WorkOrderResponse response = service.changeStatus(2L, request, 42L, false, 0L);

        assertThat(response.status()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(response.startedAt()).isNotNull();
    }

    @Test
    void changeStatusInProgressToCompletedWithEvidenceSetsCompletedAt() {
        WorkOrder order = createSampleOrder(3L, OrderStatus.IN_PROGRESS, 42L, 0L);
        when(workOrderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(evidenceRepository.existsByWorkOrderId(3L)).thenReturn(true);

        ChangeStatusRequest request = new ChangeStatusRequest(OrderStatus.COMPLETED, "Work done");
        WorkOrderResponse response = service.changeStatus(3L, request, 42L, false, 0L);

        assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
    }

    @Test
    void changeStatusInProgressToCompletedWithoutEvidenceThrowsException() {
        WorkOrder order = createSampleOrder(4L, OrderStatus.IN_PROGRESS, 42L, 0L);
        when(workOrderRepository.findById(4L)).thenReturn(Optional.of(order));
        when(evidenceRepository.existsByWorkOrderId(4L)).thenReturn(false);

        ChangeStatusRequest request = new ChangeStatusRequest(OrderStatus.COMPLETED, "Work done");

        assertThatThrownBy(() -> service.changeStatus(4L, request, 42L, false, 0L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("evidence");
    }

    @Test
    void changeStatusInvalidTransitionThrowsException() {
        WorkOrder order = createSampleOrder(5L, OrderStatus.DRAFT, null, 0L);
        when(workOrderRepository.findById(5L)).thenReturn(Optional.of(order));

        ChangeStatusRequest request = new ChangeStatusRequest(OrderStatus.COMPLETED, "Illegal skip");

        assertThatThrownBy(() -> service.changeStatus(5L, request, 1L, true, 0L))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void technicianCannotModifyOtherTechnicianOrder() {
        WorkOrder order = createSampleOrder(6L, OrderStatus.ASSIGNED, 42L, 0L);
        when(workOrderRepository.findById(6L)).thenReturn(Optional.of(order));

        ChangeStatusRequest request = new ChangeStatusRequest(OrderStatus.IN_PROGRESS, "Unauthorized attempt");

        assertThatThrownBy(() -> service.changeStatus(6L, request, 999L, false, 0L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void technicianCannotCancelOrder() {
        WorkOrder order = createSampleOrder(7L, OrderStatus.ASSIGNED, 42L, 0L);
        when(workOrderRepository.findById(7L)).thenReturn(Optional.of(order));

        ChangeStatusRequest request = new ChangeStatusRequest(OrderStatus.CANCELLED, "Technician trying to cancel");

        assertThatThrownBy(() -> service.changeStatus(7L, request, 42L, false, 0L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("supervisors");
    }
    @Test
    void findWorkOrdersAsTechnicianRestrictsToCurrentUserId() {
        WorkOrder order = createSampleOrder(10L, OrderStatus.ASSIGNED, 42L, 0L);
        org.springframework.data.domain.Page<WorkOrder> page = new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(order),
                org.springframework.data.domain.PageRequest.of(0, 20),
                1
        );

        when(workOrderRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        com.fieldops.orders.application.dto.PageResponse<com.fieldops.orders.application.dto.WorkOrderSummaryResponse> result =
                service.findWorkOrders(null, 999L, null, null, null, null,
                        org.springframework.data.domain.PageRequest.of(0, 20), 42L, false);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().getFirst().assignedTechnicianId()).isEqualTo(42L);
    }

    @Test
    void getWorkOrderByIdAsTechnicianForDifferentOrderThrowsAccessDenied() {
        WorkOrder order = createSampleOrder(11L, OrderStatus.ASSIGNED, 42L, 0L);
        when(workOrderRepository.findById(11L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getWorkOrderById(11L, 999L, false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getWorkOrderByIdAsSupervisorReturnsOrder() {
        WorkOrder order = createSampleOrder(12L, OrderStatus.DRAFT, null, 0L);
        when(workOrderRepository.findById(12L)).thenReturn(Optional.of(order));

        WorkOrderResponse response = service.getWorkOrderById(12L, 1L, true);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(12L);
    }
    @Test
    void getMetricsCalculatesTotalsAndAggregates() {
        when(workOrderRepository.count()).thenReturn(10L);
        when(workOrderRepository.countGroupedByStatus()).thenReturn(java.util.List.of(
                new Object[]{OrderStatus.COMPLETED, 6L},
                new Object[]{OrderStatus.IN_PROGRESS, 4L}
        ));
        when(workOrderRepository.countGroupedByPriority()).thenReturn(java.util.List.of(
                new Object[]{Priority.HIGH, 7L},
                new Object[]{Priority.MEDIUM, 3L}
        ));
        LocalDateTime now = LocalDateTime.now();
        when(workOrderRepository.findCompletedDurations()).thenReturn(java.util.List.of(
                new Object[]{now.minusMinutes(60), now},
                new Object[]{now.minusMinutes(120), now}
        ));

        com.fieldops.orders.application.dto.WorkOrderMetricsResponse metrics = service.getMetrics();

        assertThat(metrics.totalOrders()).isEqualTo(10L);
        assertThat(metrics.ordersByStatus().get(OrderStatus.COMPLETED)).isEqualTo(6L);
        assertThat(metrics.ordersByStatus().get(OrderStatus.DRAFT)).isEqualTo(0L);
        assertThat(metrics.ordersByPriority().get(Priority.HIGH)).isEqualTo(7L);
        assertThat(metrics.avgDurationMinutes()).isEqualByComparingTo("90.00");
    }
}