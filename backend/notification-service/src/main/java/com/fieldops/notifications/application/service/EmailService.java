package com.fieldops.notifications.application.service;

public interface EmailService {

    void sendOrderAssignedNotification(String recipient, String technicianName, String orderCode, String scheduledAt);

    void sendOrderCompletedNotification(String recipient, String orderCode, String technicianId, int durationMinutes, int evidenceCount, String completedAt);

    default void send(NotificationPlan plan) {
        plan.send(this);
    }
}
