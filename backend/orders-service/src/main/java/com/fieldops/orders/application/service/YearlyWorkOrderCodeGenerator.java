package com.fieldops.orders.application.service;

import com.fieldops.orders.infrastructure.persistence.WorkOrderRepository;
import org.springframework.stereotype.Component;

import java.time.Year;

@Component
public class YearlyWorkOrderCodeGenerator implements WorkOrderCodeGenerator {

    private final WorkOrderRepository workOrderRepository;

    public YearlyWorkOrderCodeGenerator(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    @Override
    public synchronized String generateNextCode() {
        int currentYear = Year.now().getValue();
        String prefix = "WO-" + currentYear + "-";
        long currentCount = workOrderRepository.countByCodePrefix(prefix);
        long nextSequence = currentCount + 1;
        return String.format("%s%05d", prefix, nextSequence);
    }
}