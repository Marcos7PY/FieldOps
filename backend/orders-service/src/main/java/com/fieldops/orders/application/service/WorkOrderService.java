package com.fieldops.orders.application.service;

import com.fieldops.orders.application.dto.CreateWorkOrderRequest;
import com.fieldops.orders.application.dto.WorkOrderResponse;
import com.fieldops.orders.domain.exception.ResourceNotFoundException;
import com.fieldops.orders.domain.model.Client;
import com.fieldops.orders.domain.model.OrderStatus;
import com.fieldops.orders.domain.model.WorkOrder;
import com.fieldops.orders.domain.model.WorkOrderStatusHistory;
import com.fieldops.orders.infrastructure.persistence.ClientRepository;
import com.fieldops.orders.infrastructure.persistence.WorkOrderRepository;
import com.fieldops.orders.infrastructure.persistence.WorkOrderStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final ClientRepository clientRepository;
    private final WorkOrderStatusHistoryRepository historyRepository;
    private final WorkOrderCodeGenerator codeGenerator;
    private final WorkOrderMapper mapper;

    public WorkOrderService(
            WorkOrderRepository workOrderRepository,
            ClientRepository clientRepository,
            WorkOrderStatusHistoryRepository historyRepository,
            WorkOrderCodeGenerator codeGenerator,
            WorkOrderMapper mapper
    ) {
        this.workOrderRepository = workOrderRepository;
        this.clientRepository = clientRepository;
        this.historyRepository = historyRepository;
        this.codeGenerator = codeGenerator;
        this.mapper = mapper;
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

        return mapper.toResponse(savedOrder);
    }
}