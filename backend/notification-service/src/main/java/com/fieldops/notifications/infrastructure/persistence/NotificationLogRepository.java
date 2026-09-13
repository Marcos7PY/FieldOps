package com.fieldops.notifications.infrastructure.persistence;

import com.fieldops.notifications.domain.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByEventId(String eventId);

    List<NotificationLog> findByRecipient(String recipient);
}
