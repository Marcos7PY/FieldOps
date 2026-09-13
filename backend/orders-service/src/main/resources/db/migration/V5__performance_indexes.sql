CREATE INDEX ix_work_order_status_scheduled_inc
ON work_order (status, scheduled_at)
${include_clause};

CREATE INDEX ix_work_order_active_metrics
ON work_order (status, scheduled_at)
${performance_filter};
