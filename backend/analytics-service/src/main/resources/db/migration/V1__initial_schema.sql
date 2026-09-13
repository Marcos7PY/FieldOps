CREATE TABLE work_order_daily_metrics (
    metric_date DATE NOT NULL,
    technician_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    order_count INT NOT NULL,
    avg_duration_minutes DECIMAL(10,2) NULL,
    updated_at DATETIME2 NOT NULL,
    CONSTRAINT pk_work_order_daily_metrics PRIMARY KEY (metric_date, technician_id, status)
);

CREATE TABLE processed_event (
    event_id VARCHAR(36) NOT NULL,
    consumer_group VARCHAR(50) NOT NULL,
    processed_at DATETIME2 NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id, consumer_group)
);

CREATE TABLE projection_checkpoint (
    consumer_group VARCHAR(50) NOT NULL,
    last_event_at DATETIME2 NULL,
    rebuilt_at DATETIME2 NULL,
    events_processed BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_projection_checkpoint PRIMARY KEY (consumer_group)
);
