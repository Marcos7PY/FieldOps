package com.fieldops.orders.application.service;

import com.fieldops.orders.domain.model.WorkOrder;

public interface OutboxService {

    void recordOrderCreated(WorkOrder order, Long createdBy);

    void recordOrderAssigned(WorkOrder order);

    void recordOrderStarted(WorkOrder order);

    void recordOrderCompleted(WorkOrder order, int evidenceCount);

    void recordOrderCancelled(WorkOrder order, Long cancelledBy, String reason);
}
