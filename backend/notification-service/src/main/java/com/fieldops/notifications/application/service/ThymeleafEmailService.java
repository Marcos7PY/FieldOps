package com.fieldops.notifications.application.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

@Service
public class ThymeleafEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ThymeleafEmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public ThymeleafEmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    public void sendOrderAssignedNotification(String recipient, String technicianName, String orderCode, String scheduledAt) {
        Context context = new Context();
        context.setVariable("technicianName", technicianName);
        context.setVariable("orderCode", orderCode);
        context.setVariable("scheduledAt", scheduledAt);

        String html = templateEngine.process("order-assigned", context);
        sendHtmlEmail(recipient, "Nueva orden asignada: " + orderCode, html);
    }

    @Override
    public void sendOrderCompletedNotification(String recipient, String orderCode, String technicianId, int durationMinutes, int evidenceCount, String completedAt) {
        Context context = new Context();
        context.setVariable("orderCode", orderCode);
        context.setVariable("technicianId", technicianId);
        context.setVariable("durationMinutes", durationMinutes);
        context.setVariable("evidenceCount", evidenceCount);
        context.setVariable("completedAt", completedAt);

        String html = templateEngine.process("order-completed", context);
        sendHtmlEmail(recipient, "Orden completada: " + orderCode, html);
    }

    private void sendHtmlEmail(String recipient, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom("notifications@fieldops.com");
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email successfully sent to {} with subject: {}", recipient, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", recipient, e.getMessage(), e);
            throw new IllegalStateException("Failed to send email", e);
        }
    }
}
