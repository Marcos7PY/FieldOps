CREATE TABLE notification_log (
    id BIGINT IDENTITY(1,1) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    recipient VARCHAR(120) NOT NULL,
    subject NVARCHAR(200) NULL,
    sent_at DATETIME2 NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_notification_log PRIMARY KEY (id)
);

CREATE INDEX ix_notification_log_event_id ON notification_log (event_id);

CREATE TABLE processed_event (
    event_id VARCHAR(36) NOT NULL,
    consumer_group VARCHAR(50) NOT NULL,
    processed_at DATETIME2 NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id, consumer_group)
);
