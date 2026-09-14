-- Renombrado de V5 a V4 para eliminar el hueco de versiones segun ADR 0008
CREATE INDEX ix_work_order_status_scheduled_inc
ON work_order (status, scheduled_at)
${include_clause};

CREATE INDEX ix_work_order_active_metrics
ON work_order (status, scheduled_at)
${performance_filter};
