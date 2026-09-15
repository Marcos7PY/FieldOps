-- Los indices de V4 estaban indexados por (status, scheduled_at) para una consulta
-- por rango de scheduled_at que nunca llego a implementarse. La consulta real
-- (findMetricsInRange) filtra por created_at. Se retiran y se sustituyen por:
--   1) un indice estrecho sobre status, para los GROUP BY globales de /metrics
--   2) un indice cubriente sobre created_at, para /metrics/range y el listado paginado
-- No se editan V4 ni V6: ya estan aplicadas y su checksum debe permanecer intacto.

DROP INDEX ix_work_order_status_scheduled_inc ON work_order;
DROP INDEX ix_work_order_active_metrics ON work_order;

CREATE INDEX ix_work_order_status ON work_order (status);

DROP INDEX ix_work_order_created_at ON work_order;

CREATE INDEX ix_work_order_created_at
ON work_order (created_at DESC)
INCLUDE (status, priority, started_at, completed_at);
