package com.fieldops.orders.application.service;

import com.fieldops.orders.application.dto.ClientResponse;
import com.fieldops.orders.application.dto.EvidenceResponse;
import com.fieldops.orders.application.dto.StatusHistoryResponse;
import com.fieldops.orders.application.dto.WorkOrderResponse;
import com.fieldops.orders.application.dto.WorkOrderSummaryResponse;
import com.fieldops.orders.domain.model.Client;
import com.fieldops.orders.domain.model.WorkOrder;
import com.fieldops.orders.domain.model.WorkOrderEvidence;
import com.fieldops.orders.domain.model.WorkOrderStatusHistory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class WorkOrderMapper {

    public ClientResponse toClientResponse(Client client) {
        if (client == null) {
            return null;
        }
        return new ClientResponse(
                client.getId(),
                client.getBusinessName(),
                client.getTaxId(),
                client.getAddress(),
                client.getLatitude(),
                client.getLongitude(),
                client.getPhone(),
                client.isActive()
        );
    }

    public EvidenceResponse toEvidenceResponse(WorkOrderEvidence evidence) {
        if (evidence == null) {
            return null;
        }
        Long orderId = evidence.getWorkOrder() != null ? evidence.getWorkOrder().getId() : null;
        String contentUrl = (orderId != null && evidence.getId() != null)
                ? "/api/v1/work-orders/" + orderId + "/evidence/" + evidence.getId() + "/content"
                : evidence.getFilePath();
        return new EvidenceResponse(
                evidence.getId(),
                orderId,
                contentUrl,
                evidence.getContentType(),
                evidence.getSizeBytes(),
                evidence.getLatitude(),
                evidence.getLongitude(),
                evidence.getCapturedAt(),
                evidence.getUploadedAt()
        );
    }

    public StatusHistoryResponse toStatusHistoryResponse(WorkOrderStatusHistory history) {
        if (history == null) {
            return null;
        }
        return new StatusHistoryResponse(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getChangedBy(),
                history.getChangedAt(),
                history.getNotes()
        );
    }

    public WorkOrderSummaryResponse toSummaryResponse(WorkOrder order) {
        if (order == null) {
            return null;
        }
        return new WorkOrderSummaryResponse(
                order.getId(),
                order.getCode(),
                order.getTitle(),
                order.getStatus(),
                order.getPriority(),
                order.getClient().getId(),
                order.getClient().getBusinessName(),
                order.getAssignedTechnicianId(),
                order.getCreatedAt(),
                order.getScheduledAt(),
                order.getVersion()
        );
    }

    public WorkOrderResponse toResponse(WorkOrder order) {
        if (order == null) {
            return null;
        }
        List<EvidenceResponse> evidences = order.getEvidences() != null
                ? order.getEvidences().stream().map(this::toEvidenceResponse).toList()
                : Collections.emptyList();

        List<StatusHistoryResponse> history = order.getStatusHistory() != null
                ? order.getStatusHistory().stream().map(this::toStatusHistoryResponse).toList()
                : Collections.emptyList();

        return new WorkOrderResponse(
                order.getId(),
                order.getCode(),
                order.getTitle(),
                order.getDescription(),
                order.getStatus(),
                order.getPriority(),
                toClientResponse(order.getClient()),
                order.getAssignedTechnicianId(),
                order.getCreatedBy(),
                order.getCreatedAt(),
                order.getScheduledAt(),
                order.getStartedAt(),
                order.getCompletedAt(),
                order.getVersion(),
                evidences,
                history
        );
    }
}
