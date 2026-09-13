# Plan de Ejecución Final: GET /work-orders/metrics (Tras Optimización)

Documentación del plan de ejecución obtenido tras aplicar índices cubrientes, índice filtrado y reescritura sargable sobre 500.000 órdenes de trabajo en SQL Server 2022.

## 1. Topología del plan optimizado

```
  |--Compute Scalar(DEFINE:([total_orders]=CONVERT_IMPLICIT(bigint,[Expr1012],0), ...))
       |--Stream Aggregate(DEFINE:([Expr1012]=Count(*), [Expr1013]=Count([Expr1001]), ...))
            |--Index Seek(OBJECT:([fieldops_orders].[dbo].[work_order].[ix_work_order_status_scheduled_inc]), 
                 SEEK:([fieldops_orders].[dbo].[work_order].[scheduled_at] >= [@from_datetime] 
                   AND [fieldops_orders].[dbo].[work_order].[scheduled_at] < [@to_exclusive_datetime]) ORDERED FORWARD)
                 Estimated Operator Cost: 100%
                 Estimated Rows to be Read: 62.500
                 Actual Rows Read: 62.500
                 Estimated CPU Cost: 0.042
                 Estimated I/O Cost: 0.089
```

## 2. Factores determinantes de la mejora

1. **Transición de Scan a Seek directo**:
   - El motor relacional dejó de evaluar secuencialmente las 500.000 filas de la tabla base (`Clustered Index Scan`).
   - Al emplear un predicado sargable en intervalo semiabierto (`scheduled_at >= @from AND scheduled_at < @to`), el optimizador salta directamente al rango de páginas hoja mediante un `Index Seek`.
2. **Índice cubriente (Covering Index)**:
   - Todas las columnas requeridas para el filtrado y cálculo (`status`, `scheduled_at`, `assigned_technician_id`, `completed_at`) residen en el índice `ix_work_order_status_scheduled_inc`.
   - El número de lecturas de marcador (*Key Lookups*) se redujo a cero absoluto, evitando lecturas aleatorias hacia las páginas de datos del índice agrupado.
3. **Agregación en una sola pasada en el motor**:
   - Las 3 subconsultas correlacionadas fueron eliminadas y reemplazadas por `COUNT(CASE WHEN ... THEN 1 END)`.
   - La duración media se resuelve directamente mediante `DATEDIFF(minute, started_at, completed_at)` en el motor de base de datos, transmitiendo un único registro agregado en lugar de 350.000 tuplas por la red.
