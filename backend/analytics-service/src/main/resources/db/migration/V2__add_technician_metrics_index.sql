-- P2-18: Secondary index on technician_id and metric_date to eliminate table scans
-- when querying technician performance by technician_id
CREATE INDEX ix_metrics_technician ON work_order_daily_metrics (technician_id, metric_date);
