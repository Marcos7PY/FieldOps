package com.fieldops.notifications.application.service;

public sealed interface NotificationPlan permits OrderAssignedPlan, OrderCompletedPlan {
    String recipient();
    String subject();
    void send(EmailService emailService);
}
