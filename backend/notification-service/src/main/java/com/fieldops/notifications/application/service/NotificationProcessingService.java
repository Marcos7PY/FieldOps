package com.fieldops.notifications.application.service;

import com.fieldops.events.avro.WorkOrderEvent;

public interface NotificationProcessingService {

    boolean isAlreadyProcessed(String eventId, String consumerGroup);

    void processAndRecord(WorkOrderEvent event, String consumerGroup);
}
