CREATE INDEX ix_work_order_completed_duration
ON work_order (completed_at)
INCLUDE (started_at)
WHERE status = 'COMPLETED';
