-- ============================================================================
-- FieldOps: docs/performance/optimized-metrics-query.sql
-- Consultas de métricas optimizadas ejecutadas por orders-service (GET /api/v1/work-orders/metrics)
-- ============================================================================

USE fieldops_orders;
GO

SET STATISTICS IO ON;
SET STATISTICS TIME ON;
GO

-- ============================================================================
-- 1. Cálculo de duración promedio de órdenes completadas
-- Invocada por: WorkOrderRepository.findAverageCompletionMinutes()
-- Apoyada por: ix_work_order_completed_duration (V5__metrics_covering_index.sql)
--
-- Antes: La aplicación transfería las fechas de 350.000 filas completadas hacia
--        la memoria JVM (~35 MB de red, ~280 MB en heap) para calcular la media en un bucle Java.
-- Después: El motor relacional calcula AVG(DATEDIFF(...)) en una única pasada
--          utilizando el índice filtrado y devuelve un único valor escalar (1 fila, < 1 KB).
-- ============================================================================

SELECT AVG(CAST(DATEDIFF(MINUTE, started_at, completed_at) AS DECIMAL(18,4))) AS avg_duration_minutes
FROM work_order
WHERE status = 'COMPLETED'
  AND started_at IS NOT NULL
  AND completed_at IS NOT NULL
  AND completed_at >= started_at;
GO

-- ============================================================================
-- 2. Conteo agrupado por estado
-- Invocada por: WorkOrderRepository.countGroupedByStatus()
-- ============================================================================

SELECT status, COUNT(*) AS count_by_status
FROM work_order
GROUP BY status;
GO

-- ============================================================================
-- 3. Conteo agrupado por prioridad
-- Invocada por: WorkOrderRepository.countGroupedByPriority()
-- ============================================================================

SELECT priority, COUNT(*) AS count_by_priority
FROM work_order
GROUP BY priority;
GO

-- ============================================================================
-- 4. Consulta analítica condicional por rango de fechas (ejemplo de reporte)
-- Reescritura 100% sargable con agregación condicional en una sola pasada
-- Apoyada por: ix_work_order_status_scheduled_inc (V4__performance_indexes.sql)
-- ============================================================================

DECLARE @from_datetime DATETIME2 = '2026-01-01 00:00:00';
DECLARE @to_exclusive_datetime DATETIME2 = '2026-04-01 00:00:00';

SELECT 
    COUNT(*) AS total_orders,
    COUNT(CASE WHEN status = 'DRAFT' THEN 1 END) AS draft_count,
    COUNT(CASE WHEN status = 'ASSIGNED' THEN 1 END) AS assigned_count,
    COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) AS in_progress_count,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS completed_count,
    COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_count,
    COUNT(CASE WHEN priority = 'LOW' THEN 1 END) AS low_priority_count,
    COUNT(CASE WHEN priority = 'MEDIUM' THEN 1 END) AS medium_priority_count,
    COUNT(CASE WHEN priority = 'HIGH' THEN 1 END) AS high_priority_count,
    COUNT(CASE WHEN priority = 'CRITICAL' THEN 1 END) AS critical_priority_count,
    CAST(AVG(CASE 
        WHEN status = 'COMPLETED' 
             AND started_at IS NOT NULL 
             AND completed_at IS NOT NULL 
             AND completed_at >= started_at 
        THEN CAST(DATEDIFF(minute, started_at, completed_at) AS DECIMAL(10,2))
        ELSE NULL 
    END) AS DECIMAL(10,2)) AS avg_duration_minutes
FROM work_order
WHERE scheduled_at >= @from_datetime 
  AND scheduled_at < @to_exclusive_datetime;
GO

SET STATISTICS IO OFF;
SET STATISTICS TIME OFF;
GO
