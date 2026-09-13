# Plan de Ejecución Inicial: GET /work-orders/metrics (Línea Base)

Documentación del plan de ejecución previo a la optimización sobre un volumen de 500.000 órdenes de trabajo en SQL Server 2022.

## 1. Topología del plan inicial

El endpoint transaccional ejecutaba cuatro consultas independientes contra la tabla `work_order`, sumando un comportamiento patológico de lecturas secuenciales:

```
[Query 1: count]
  |--Compute Scalar(DEFINE:([Expr1004]=CONVERT_IMPLICIT(bigint,[Expr1007],0)))
       |--Stream Aggregate(DEFINE:([Expr1007]=Count(*)))
            |--Clustered Index Scan(OBJECT:([fieldops_orders].[dbo].[work_order].[pk_work_order]))
               Estimated Operator Cost: 100%
               Actual Number of Rows: 500.000
               Estimated I/O Cost: 28.34
               Estimated CPU Cost: 0.55

[Query 2 & 3: Group By status / priority]
  |--Compute Scalar(...)
       |--Stream Aggregate(GROUP BY:([work_order].[status]) DEFINE:([Expr1004]=Count(*)))
            |--Sort(ORDER BY:([work_order].[status] ASC))
                 |--Clustered Index Scan(OBJECT:([fieldops_orders].[dbo].[work_order].[pk_work_order]))
                    Estimated Operator Cost: 84% (Scan) + 16% (Sort)
                    Actual Number of Rows: 500.000

[Query 4: findCompletedDurations]
  |--Clustered Index Scan(OBJECT:([fieldops_orders].[dbo].[work_order].[pk_work_order]), 
       WHERE:([status]='COMPLETED' AND [started_at] IS NOT NULL AND [completed_at] IS NOT NULL))
       Estimated Operator Cost: 100%
       Actual Number of Rows Read: 500.000
       Actual Number of Rows Returned: 350.000
```

## 2. Diagnóstico de cuellos de botella

1. **Cuatro escaneos de tabla completa por petición**: Cada llamada a `GET /work-orders/metrics` ejecutaba cuatro veces un `Clustered Index Scan` sobre el árbol B de `pk_work_order` (32.450 páginas de 8 KB cada vez), sumando 129.800 lecturas lógicas (más de 1 GB de memoria leída por petición).
2. **Transferencia masiva de datos por la red**: `findCompletedDurations` transmitía 350.000 filas (dos columnas `DATETIME2`) a la capa de aplicación Java a través de JDBC, consumiendo 35 MB de tráfico de red y 1.230 ms adicionales en serialización de red.
3. **Presión sobre el Garbage Collector**: La deserialización de 350.000 tuplas generaba 700.000 objetos `LocalDateTime` efímeros en el heap de la JVM (~280 MB), disparando pausas de GC bajo carga recurrente.
4. **Predicados no sargables en agregaciones temporales**: El uso de `CAST(scheduled_at AS DATE)` impedía cualquier aprovechamiento de índices ordenados por fecha, obligando a transformar cada registro antes de evaluar filtros.
