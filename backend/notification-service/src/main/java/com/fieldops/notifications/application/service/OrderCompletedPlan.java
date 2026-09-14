package com.fieldops.notifications.application.service;

public record OrderCompletedPlan(
        String recipient,
        String orderCode,
        String technicianId,
        int durationMinutes,
        int evidenceCount,
        String completedAt
) implements NotificationPlan {

    @Override
    public String subject() {
        return "Orden completada: " + orderCode;
    }

    @Override
    public void send(EmailService emailService) {
        emailService.sendOrderCompletedNotification(
                recipient,
                orderCode,
                technicianId,
                durationMinutes,
                evidenceCount,
                completedAt
        );
    }
}
