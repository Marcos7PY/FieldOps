CREATE TABLE client (
    id BIGINT IDENTITY(1,1) NOT NULL,
    business_name NVARCHAR(150) NOT NULL,
    tax_id VARCHAR(20) NOT NULL,
    address NVARCHAR(250) NULL,
    latitude DECIMAL(9,6) NULL,
    longitude DECIMAL(9,6) NULL,
    phone VARCHAR(20) NULL,
    active BIT NOT NULL DEFAULT 1,
    CONSTRAINT pk_client PRIMARY KEY (id),
    CONSTRAINT uk_client_tax_id UNIQUE (tax_id)
);

CREATE TABLE work_order (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(20) NOT NULL,
    title NVARCHAR(150) NOT NULL,
    description NVARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL,
    priority VARCHAR(10) NOT NULL,
    client_id BIGINT NOT NULL,
    assigned_technician_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME2 NOT NULL,
    scheduled_at DATETIME2 NULL,
    started_at DATETIME2 NULL,
    completed_at DATETIME2 NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_work_order PRIMARY KEY (id),
    CONSTRAINT uk_work_order_code UNIQUE (code),
    CONSTRAINT fk_work_order_client FOREIGN KEY (client_id) REFERENCES client (id)
);

CREATE INDEX ix_work_order_client_id ON work_order (client_id);
CREATE INDEX ix_work_order_assigned_technician ON work_order (assigned_technician_id);

CREATE TABLE work_order_evidence (
    id BIGINT IDENTITY(1,1) NOT NULL,
    work_order_id BIGINT NOT NULL,
    file_path NVARCHAR(300) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    latitude DECIMAL(9,6) NULL,
    longitude DECIMAL(9,6) NULL,
    captured_at DATETIME2 NOT NULL,
    uploaded_at DATETIME2 NOT NULL,
    CONSTRAINT pk_work_order_evidence PRIMARY KEY (id),
    CONSTRAINT fk_work_order_evidence_work_order FOREIGN KEY (work_order_id) REFERENCES work_order (id)
);

CREATE INDEX ix_work_order_evidence_work_order_id ON work_order_evidence (work_order_id);

CREATE TABLE work_order_status_history (
    id BIGINT IDENTITY(1,1) NOT NULL,
    work_order_id BIGINT NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_by BIGINT NOT NULL,
    changed_at DATETIME2 NOT NULL,
    notes NVARCHAR(500) NULL,
    CONSTRAINT pk_work_order_status_history PRIMARY KEY (id),
    CONSTRAINT fk_work_order_status_history_work_order FOREIGN KEY (work_order_id) REFERENCES work_order (id)
);

CREATE INDEX ix_work_order_status_history_work_order_id ON work_order_status_history (work_order_id);