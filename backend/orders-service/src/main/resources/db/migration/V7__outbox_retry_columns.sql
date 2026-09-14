ALTER TABLE outbox_event ADD attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE outbox_event ADD last_error NVARCHAR(500) NULL;
ALTER TABLE outbox_event ADD dead_lettered_at DATETIME2 NULL;
