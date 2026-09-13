package com.fieldops.analytics.application.service;

import com.fieldops.events.avro.WorkOrderEvent;

public interface AnalyticsProjectionService {

    boolean isAlreadyProcessed(String eventId, String consumerGroup);

    void projectEvent(WorkOrderEvent event, String consumerGroup);
}
