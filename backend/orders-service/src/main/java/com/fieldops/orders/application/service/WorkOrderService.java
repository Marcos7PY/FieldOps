package com.fieldops.orders.application.service;

import com.fieldops.orders.application.dto.AssignWorkOrderRequest;
import com.fieldops.orders.application.dto.ChangeStatusRequest;
import com.fieldops.orders.application.dto.CreateWorkOrderRequest;
import com.fieldops.orders.application.dto.WorkOrderResponse;
import com.fieldops.orders.application.dto.WorkOrderSummaryResponse;
import com.fieldops.orders.application.dto.PageResponse;
import com.fieldops.orders.application.dto.WorkOrderMetricsResponse;
import com.fieldops.orders.domain.model.Priority;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import com.fieldops.orders.domain.exception.BusinessRuleViolationException;
import com.fieldops.orders.domain.exception.InvalidStatusTransitionException;
import com.fieldops.orders.domain.exception.ResourceNotFoundException;
import com.fieldops.orders.domain.exception.VersionConflictException;
import com.fieldops.orders.domain.model.Client;
import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.WorkOrder;
import com.fieldops.orders.domain.model.WorkOrderStatusHistory;
import com.fieldops.orders.infrastructure.persistence.ClientRepository;
import com.fieldops.orders.infrastructure.persistence.WorkOrderEvidenceRepository;
import com.fieldops.orders.infrastructure.persistence.WorkOrderRepository;
import com.fieldops.orders.infrastructure.persistence.WorkOrderStatusHistoryRepository;
import com.fieldops.orders.domain.exception.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Transactional
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final ClientRepository clientRepository;
    private final WorkOrderEvidenceRepository evidenceRepository;
    private final WorkOrderStatusHistoryRepository historyRepository;
    private final WorkOrderCodeGenerator codeGenerator;
    private final WorkOrderMapper mapper;
    private final OutboxService outboxService;

    public WorkOrderService(
            WorkOrderRepository workOrderRepository,
            ClientRepository clientRepository,
            WorkOrderEvidenceRepository evidenceRepository,
            WorkOrderStatusHistoryRepository historyRepository,
            WorkOrderCodeGenerator codeGenerator,
            WorkOrderMapper mapper,
            OutboxService outboxService
    ) {
        this.workOrderRepository = workOrderRepository;
        this.clientRepository = clientRepository;
        this.evidenceRepository = evidenceRepository;
        this.historyRepository = historyRepository;
        this.codeGenerator = codeGenerator;
        this.mapper = mapper;
        this.outboxService = outboxService;
    }

    public WorkOrderResponse createWorkOrder(CreateWorkOrderRequest request, Long createdBy) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + request.clientId()));

        LocalDateTime now = LocalDateTime.now();
        String code = codeGenerator.generateNextCode();

        WorkOrder order = new WorkOrder();
        order.setCode(code);
        order.setTitle(request.title().trim());
        order.setDescription(request.description() != null ? request.description().trim() : null);
        order.setPriority(request.priority());
        order.setClient(client);
        order.setCreatedBy(createdBy);
        order.setCreatedAt(now);
        order.setScheduledAt(request.scheduledAt());

        OrderStatus initialStatus = request.assignedTechnicianId() != null
                ? OrderStatus.ASSIGNED
                : OrderStatus.DRAFT;

        order.setStatus(initialStatus);
        order.setAssignedTechnicianId(request.assignedTechnicianId());

        WorkOrder savedOrder = workOrderRepository.save(order);

        WorkOrderStatusHistory history = new WorkOrderStatusHistory();
        history.setWorkOrder(savedOrder);
        history.setPreviousStatus(null);
        history.setNewStatus(initialStatus);
        history.setChangedBy(createdBy);
        history.setChangedAt(now);
        history.setNotes("Order created");
        historyRepository.save(history);

        savedOrder.getStatusHistory().add(history);

        outboxService.recordOrderCreated(savedOrder, createdBy);
        if (savedOrder.getAssignedTechnicianId() != null) {
            outboxService.recordOrderAssigned(savedOrder);
        }

        return mapper.toResponse(savedOrder);
    }

    public WorkOrderResponse assignWorkOrder(Long id, AssignWorkOrderRequest request, Long supervisorId, Long expectedVersion) {
        WorkOrder order = findOrderById(id);
        verifyOptimisticLock(order, expectedVersion);

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Cannot reassign a terminal order in status " + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        OrderStatus previousStatus = order.getStatus();
        boolean statusChanged = false;

        if (order.getStatus() == OrderStatus.DRAFT) {
            if (!order.getStatus().canTransitionTo(OrderStatus.ASSIGNED)) {
                throw new InvalidStatusTransitionException(order.getStatus(), OrderStatus.ASSIGNED);
            }
            order.setStatus(OrderStatus.ASSIGNED);
            statusChanged = true;
        }

        order.setAssignedTechnicianId(request.technicianId());
        if (request.scheduledAt() != null) {
            order.setScheduledAt(request.scheduledAt());
        }

        if (statusChanged) {
            WorkOrderStatusHistory history = new WorkOrderStatusHistory();
            history.setWorkOrder(order);
            history.setPreviousStatus(previousStatus);
            history.setNewStatus(OrderStatus.ASSIGNED);
            history.setChangedBy(supervisorId);
            history.setChangedAt(now);
            history.setNotes("Technician assigned");
            historyRepository.save(history);
            order.getStatusHistory().add(history);
        }

        WorkOrder saved = workOrderRepository.saveAndFlush(order);
        outboxService.recordOrderAssigned(saved);
        return mapper.toResponse(saved);
    }

    public WorkOrderResponse changeStatus(
            Long id,
            ChangeStatusRequest request,
            Long userId,
            boolean isSupervisor,
            Long expectedVersion
    ) {
        WorkOrder order = findOrderById(id);
        verifyOptimisticLock(order, expectedVersion);

        if (!isSupervisor) {
            if (order.getAssignedTechnicianId() == null || !order.getAssignedTechnicianId().equals(userId)) {
                throw new AccessDeniedException("Technician can only modify orders assigned to them");
            }
        }

        if (request.newStatus() == OrderStatus.CANCELLED && !isSupervisor) {
            throw new BusinessRuleViolationException("Only supervisors can cancel work orders");
        }

        if (!order.getStatus().canTransitionTo(request.newStatus())) {
            throw new InvalidStatusTransitionException(order.getStatus(), request.newStatus());
        }

        if (request.newStatus() == OrderStatus.ASSIGNED && order.getAssignedTechnicianId() == null) {
            throw new BusinessRuleViolationException("Cannot transition to ASSIGNED without an assigned technician");
        }

        if (request.newStatus() == OrderStatus.COMPLETED) {
            boolean hasEvidence = evidenceRepository.existsByWorkOrderId(id)
                    || (order.getEvidences() != null && !order.getEvidences().isEmpty());
            if (!hasEvidence) {
                throw new BusinessRuleViolationException("Cannot complete work order without at least one evidence registered");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        OrderStatus previousStatus = order.getStatus();
        order.setStatus(request.newStatus());

        if (request.newStatus() == OrderStatus.IN_PROGRESS) {
            order.setStartedAt(now);
        } else if (request.newStatus() == OrderStatus.COMPLETED) {
            order.setCompletedAt(now);
        }

        WorkOrderStatusHistory history = new WorkOrderStatusHistory();
        history.setWorkOrder(order);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(request.newStatus());
        history.setChangedBy(userId);
        history.setChangedAt(now);
        history.setNotes(request.notes());
        historyRepository.save(history);
        order.getStatusHistory().add(history);

        WorkOrder saved = workOrderRepository.saveAndFlush(order);

        if (request.newStatus() == OrderStatus.IN_PROGRESS) {
            outboxService.recordOrderStarted(saved);
        } else if (request.newStatus() == OrderStatus.COMPLETED) {
            int evidenceCount = saved.getEvidences() != null && !saved.getEvidences().isEmpty()
                    ? saved.getEvidences().size()
                    : (int) evidenceRepository.countByWorkOrderId(id);
            outboxService.recordOrderCompleted(saved, evidenceCount);
        } else if (request.newStatus() == OrderStatus.CANCELLED) {
            outboxService.recordOrderCancelled(saved, userId, request.notes());
        }

        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkOrderSummaryResponse> findWorkOrders(
            OrderStatus status,
            Long technicianId,
            Long clientId,
            LocalDateTime from,
            LocalDateTime to,
            String search,
            org.springframework.data.domain.Pageable pageable,
            Long currentUserId,
            boolean isSupervisor
    ) {
        Long effectiveTechnicianId = isSupervisor ? technicianId : currentUserId;

        org.springframework.data.jpa.domain.Specification<WorkOrder> spec =
                com.fieldops.orders.infrastructure.persistence.WorkOrderPredicates.withFilters(
                        status, effectiveTechnicianId, clientId, from, to, search
                );

        org.springframework.data.domain.Page<WorkOrder> page = workOrderRepository.findAll(spec, pageable);
        java.util.List<com.fieldops.orders.application.dto.WorkOrderSummaryResponse> content =
                page.getContent().stream().map(mapper::toSummaryResponse).toList();

        return new com.fieldops.orders.application.dto.PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse getWorkOrderById(Long id, Long currentUserId, boolean isSupervisor) {
        WorkOrder order = findOrderById(id);
        if (!isSupervisor) {
            if (order.getAssignedTechnicianId() == null || !order.getAssignedTechnicianId().equals(currentUserId)) {
                throw new AccessDeniedException("Technician can only view orders assigned to them");
            }
        }
        return mapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public WorkOrderMetricsResponse getMetrics() {
        long totalOrders = workOrderRepository.count();

        Map<OrderStatus, Long> ordersByStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            ordersByStatus.put(status, 0L);
        }
        for (Object[] row : workOrderRepository.countGroupedByStatus()) {
            OrderStatus status = (OrderStatus) row[0];
            Long count = (Long) row[1];
            ordersByStatus.put(status, count);
        }

        Map<Priority, Long> ordersByPriority = new EnumMap<>(Priority.class);
        for (Priority priority : Priority.values()) {
            ordersByPriority.put(priority, 0L);
        }
        for (Object[] row : workOrderRepository.countGroupedByPriority()) {
            Priority priority = (Priority) row[0];
            Long count = (Long) row[1];
            ordersByPriority.put(priority, count);
        }

        BigDecimal avgDuration = workOrderRepository.findAverageCompletionMinutes();
        if (avgDuration != null) {
            avgDuration = avgDuration.setScale(2, RoundingMode.HALF_UP);
        }

        return new WorkOrderMetricsResponse(totalOrders, ordersByStatus, ordersByPriority, avgDuration);
    }

    public WorkOrder findOrderById(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found with id: " + id));
    }

    private void verifyOptimisticLock(WorkOrder order, Long expectedVersion) {
        if (expectedVersion != null && !Objects.equals(order.getVersion(), expectedVersion)) {
            throw new VersionConflictException(String.format(
                    "Work order %d has version %d, but expected version was %d",
                    order.getId(), order.getVersion(), expectedVersion
            ));
        }
    }
}
