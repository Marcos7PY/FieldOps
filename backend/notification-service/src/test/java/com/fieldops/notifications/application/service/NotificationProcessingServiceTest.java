package com.fieldops.notifications.application.service;

import com.fieldops.events.avro.OrderAssignedPayload;
import com.fieldops.events.avro.OrderCompletedPayload;
import com.fieldops.events.avro.OrderCreatedPayload;
import com.fieldops.events.avro.WorkOrderEvent;
import com.fieldops.notifications.domain.model.ProcessedEventId;
import com.fieldops.notifications.infrastructure.client.UserDirectoryClient;
import com.fieldops.notifications.infrastructure.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationProcessingServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationLogWriter notificationLogWriter;

    @Mock
    private EmailService emailService;

    @Mock
    private UserDirectoryClient userDirectoryClient;

    private NotificationProcessingService service;

    @BeforeEach
    void setUp() {
        service = new DefaultNotificationProcessingService(
                processedEventRepository,
                notificationLogWriter,
                emailService,
                userDirectoryClient
        );
    }

    @Test
    void shouldReturnTrueWhenEventIsAlreadyProcessed() {
        String eventId = UUID.randomUUID().toString();
        ProcessedEventId id = new ProcessedEventId(eventId, "notification-group");
        when(processedEventRepository.existsById(id)).thenReturn(true);

        assertThat(service.isAlreadyProcessed(eventId, "notification-group")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenEventIsNotProcessed() {
        String eventId = UUID.randomUUID().toString();
        ProcessedEventId id = new ProcessedEventId(eventId, "notification-group");
        when(processedEventRepository.existsById(id)).thenReturn(false);

        assertThat(service.isAlreadyProcessed(eventId, "notification-group")).isFalse();
    }

    @Test
    void shouldProcessOrderAssignedWithResolvedUserAndSaveProcessedEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant scheduledAt = Instant.now();

        when(userDirectoryClient.findUserById(42L)).thenReturn(Optional.of(new UserDirectoryClient.UserDto(
                42L, "tecnico2", "Carlos Gomez Real", "otro@ejemplo.com", List.of("ROLE_TECHNICIAN")
        )));
        when(notificationLogWriter.recordAttempt(any(), eq(eventId))).thenReturn(100L);

        OrderAssignedPayload payload = OrderAssignedPayload.newBuilder()
                .setTechnicianId("42")
                .setScheduledAt(scheduledAt)
                .build();

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_ASSIGNED")
                .setOrderId(101L)
                .setOrderCode("ORD-2026-0001")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(payload)
                .build();

        service.processAndRecord(event, "notification-group");

        ArgumentCaptor<NotificationPlan> planCaptor = ArgumentCaptor.forClass(NotificationPlan.class);
        verify(emailService).send(planCaptor.capture());
        assertThat(planCaptor.getValue().recipient()).isEqualTo("otro@ejemplo.com");
        assertThat(((OrderAssignedPlan) planCaptor.getValue()).technicianName()).isEqualTo("Carlos Gomez Real");

        verify(notificationLogWriter).recordAttempt(any(), eq(eventId));
        verify(notificationLogWriter).markSent(100L);
        verify(notificationLogWriter).markProcessed(eventId, "notification-group");
    }

    @Test
    void shouldProcessOrderCompletedWithResolvedCreatedByAndSaveProcessedEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant completedAt = Instant.now();

        when(userDirectoryClient.findUserById(5L)).thenReturn(Optional.of(new UserDirectoryClient.UserDto(
                5L, "supervisor1", "Ana Supervisor", "ana.supervisor@fieldops.com", List.of("ROLE_SUPERVISOR")
        )));
        when(notificationLogWriter.recordAttempt(any(), eq(eventId))).thenReturn(101L);

        OrderCompletedPayload payload = OrderCompletedPayload.newBuilder()
                .setTechnicianId("42")
                .setCompletedAt(completedAt)
                .setDurationMinutes(90)
                .setEvidenceCount(2)
                .setCreatedBy("5")
                .build();

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_COMPLETED")
                .setOrderId(102L)
                .setOrderCode("ORD-2026-0002")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(payload)
                .build();

        service.processAndRecord(event, "notification-group");

        ArgumentCaptor<NotificationPlan> planCaptor = ArgumentCaptor.forClass(NotificationPlan.class);
        verify(emailService).send(planCaptor.capture());
        assertThat(planCaptor.getValue().recipient()).isEqualTo("ana.supervisor@fieldops.com");

        verify(notificationLogWriter).markSent(101L);
        verify(notificationLogWriter).markProcessed(eventId, "notification-group");
    }

    @Test
    void shouldMarkFailedAndNotMarkProcessedWhenEmailFails() {
        String eventId = UUID.randomUUID().toString();
        when(notificationLogWriter.recordAttempt(any(), eq(eventId))).thenReturn(102L);
        doThrow(new MailSendException("SMTP connection refused")).when(emailService).send(any(NotificationPlan.class));

        OrderAssignedPayload payload = OrderAssignedPayload.newBuilder()
                .setTechnicianId("42")
                .setTechnicianEmail("carlos@fieldops.com")
                .setScheduledAt(Instant.now())
                .build();

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_ASSIGNED")
                .setOrderId(101L)
                .setOrderCode("ORD-2026-0001")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(payload)
                .build();

        assertThatThrownBy(() -> service.processAndRecord(event, "notification-group"))
                .isInstanceOf(MailSendException.class);

        verify(notificationLogWriter).markFailed(102L, "SMTP connection refused");
        verify(notificationLogWriter, never()).markSent(any());
        verify(notificationLogWriter, never()).markProcessed(any(), any());
    }

    @Test
    void shouldRecordSkippedWhenRecipientCannotBeResolved() {
        String eventId = UUID.randomUUID().toString();
        when(userDirectoryClient.findUserById(42L)).thenReturn(Optional.empty());

        OrderAssignedPayload payload = OrderAssignedPayload.newBuilder()
                .setTechnicianId("42")
                .setScheduledAt(Instant.now())
                .build();

        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_ASSIGNED")
                .setOrderId(101L)
                .setOrderCode("ORD-2026-0001")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(payload)
                .build();

        service.processAndRecord(event, "notification-group");

        verify(notificationLogWriter).recordSkipped(eq(eventId), any());
        verify(notificationLogWriter).markProcessed(eventId, "notification-group");
        verify(emailService, never()).send(any());
    }

    @Test
    void shouldRecordProcessedEventForIgnoredTypes() {
        String eventId = UUID.randomUUID().toString();
        WorkOrderEvent event = WorkOrderEvent.newBuilder()
                .setEventId(eventId)
                .setEventType("ORDER_CREATED")
                .setOrderId(103L)
                .setOrderCode("ORD-2026-0003")
                .setOccurredAt(Instant.now())
                .setSchemaVersion(1)
                .setPayload(new OrderCreatedPayload("T", 1L, "C", "LOW", "u"))
                .build();

        service.processAndRecord(event, "notification-group");

        verify(notificationLogWriter).markProcessed(eventId, "notification-group");
        verify(notificationLogWriter, never()).recordAttempt(any(), any());
    }
}
