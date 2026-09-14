package com.fieldops.notifications.application.service;

public record OrderAssignedPlan(
        String recipient,
        String technicianName,
        String orderCode,
        String scheduledAt
) implements NotificationPlan {

    @Override
    public String subject() {
        return "Nueva orden asignada: " + orderCode;
    }

    @Override
    public void send(EmailService emailService) {
        emailService.sendOrderAssignedNotification(recipient, technicianName, orderCode, scheduledAt);
    }
}
