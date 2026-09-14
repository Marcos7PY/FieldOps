-- Indice cubriente para calculo de duracion promedio de ordenes completadas (F3-T09)
CREATE INDEX ix_work_order_completed_duration
ON work_order (status)
${duration_include_clause}
${duration_metrics_filter};
