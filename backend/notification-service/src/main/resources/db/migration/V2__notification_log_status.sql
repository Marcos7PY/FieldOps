ALTER TABLE notification_log ADD error_message NVARCHAR(1000) NULL;
ALTER TABLE notification_log ADD attempts INT NOT NULL DEFAULT 0;
