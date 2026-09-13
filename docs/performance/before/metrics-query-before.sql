-- ============================================================================
-- FieldOps: docs/performance/before/metrics-query-before.sql
-- Consulta de métricas antes de optimización sobre 500.000 órdenes
-- ============================================================================

USE fieldops_orders;
GO

SET STATISTICS IO ON;
SET STATISTICS TIME ON;
GO

-- 1. Conteo total de órdenes (ejecutado por JpaRepository.count())
SELECT COUNT(*) 
FROM work_order;
GO

-- 2. Conteo agrupado por estado
SELECT w.status, COUNT(w.id) 
FROM work_order w 
GROUP BY w.status;
GO

-- 3. Conteo agrupado por prioridad
SELECT w.priority, COUNT(w.id) 
FROM work_order w 
GROUP BY w.priority;
GO

-- 4. Recuperación masiva de tuplas para cálculo de duración en memoria JVM
SELECT w.started_at, w.completed_at 
FROM work_order w 
WHERE w.status = 'COMPLETED' 
  AND w.started_at IS NOT NULL 
  AND w.completed_at IS NOT NULL;
GO

-- 5. Consulta analítica de rango con predicados no sargables y subconsultas
SELECT 
    COUNT(*) AS total_orders,
    (SELECT COUNT(*) FROM work_order w1 
     WHERE w1.status = 'COMPLETED' 
       AND CAST(w1.scheduled_at AS DATE) = CAST(w.scheduled_at AS DATE)) AS completed_count,
    (SELECT COUNT(*) FROM work_order w2 
     WHERE w2.status = 'IN_PROGRESS' 
       AND CAST(w2.scheduled_at AS DATE) = CAST(w.scheduled_at AS DATE)) AS in_progress_count,
    (SELECT AVG(DATEDIFF(minute, w3.started_at, w3.completed_at)) 
     FROM work_order w3 
     WHERE w3.status = 'COMPLETED' 
       AND CAST(w3.scheduled_at AS DATE) = CAST(w.scheduled_at AS DATE)) AS avg_duration
FROM work_order w
WHERE CAST(w.scheduled_at AS DATE) >= '2026-01-01' 
  AND CAST(w.scheduled_at AS DATE) < '2026-04-01'
GROUP BY CAST(w.scheduled_at AS DATE);
GO

SET STATISTICS IO OFF;
SET STATISTICS TIME OFF;
GO
