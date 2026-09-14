CREATE INDEX ix_work_order_status_scheduled_inc
ON work_order (status, scheduled_at)
INCLUDE (assigned_technician_id, completed_at);

CREATE INDEX ix_work_order_active_metrics
ON work_order (status, scheduled_at)
WHERE status <> 'CANCELLED';
