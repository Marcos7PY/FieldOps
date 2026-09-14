CREATE TABLE outbox_event (
    id BIGINT IDENTITY(1,1) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    payload NVARCHAR(MAX) NOT NULL,
    created_at DATETIME2 NOT NULL,
    published_at DATETIME2 NULL,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT uk_outbox_event_event_id UNIQUE (event_id)
);

CREATE INDEX ix_outbox_pending ON outbox_event (created_at) WHERE published_at IS NULL;
