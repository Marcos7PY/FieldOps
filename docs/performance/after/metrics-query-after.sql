-- ============================================================================
-- FieldOps: docs/performance/after/metrics-query-after.sql
-- Consulta de métricas tras optimización con índice cubriente y sargabilidad
-- ============================================================================

USE fieldops_orders;
GO

SET STATISTICS IO ON;
SET STATISTICS TIME ON;
GO

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
FROM work_order WITH (INDEX(ix_work_order_status_scheduled_inc))
WHERE scheduled_at >= @from_datetime 
  AND scheduled_at < @to_exclusive_datetime;
GO

SET STATISTICS IO OFF;
SET STATISTICS TIME OFF;
GO
