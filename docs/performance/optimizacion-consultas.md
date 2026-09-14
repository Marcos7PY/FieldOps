# Optimización de Consultas y Análisis de Rendimiento en FieldOps

Informe técnico del proceso de optimización del módulo de reportería y consulta de órdenes de trabajo sobre un volumen de 500.000 registros en SQL Server 2022.

## 1. Consulta original

El cálculo de métricas en el servicio transaccional combinaba cuatro consultas secuenciales en la capa de persistencia y procesaba la duración promedio mediante extracción masiva a la memoria de la aplicación:

```sql
-- Conteo de volumen total
SELECT COUNT(*) FROM work_order;

-- Agrupaciones independientes por estado y prioridad
SELECT status, COUNT(*) FROM work_order GROUP BY status;
SELECT priority, COUNT(*) FROM work_order GROUP BY priority;

-- Recuperación masiva de tuplas hacia la JVM
SELECT started_at, completed_at 
FROM work_order 
WHERE status = 'COMPLETED' 
  AND started_at IS NOT NULL 
  AND completed_at IS NOT NULL;
```

Para reportes filtrados por fecha, la consulta habitual recurría a transformaciones escalares sobre la columna de fecha y subconsultas correlacionadas por cada grupo:

```sql
SELECT 
    COUNT(*) AS total_orders,
    (SELECT COUNT(*) FROM work_order w1 
     WHERE w1.status = 'COMPLETED' AND CAST(w1.scheduled_at AS DATE) = CAST(w.scheduled_at AS DATE)) AS completed_count,
    (SELECT COUNT(*) FROM work_order w2 
     WHERE w2.status = 'IN_PROGRESS' AND CAST(w2.scheduled_at AS DATE) = CAST(w.scheduled_at AS DATE)) AS in_progress_count,
    (SELECT AVG(DATEDIFF(minute, w3.started_at, w3.completed_at)) 
     FROM work_order w3 
     WHERE w3.status = 'COMPLETED' AND CAST(w3.scheduled_at AS DATE) = CAST(w.scheduled_at AS DATE)) AS avg_duration
FROM work_order w
WHERE CAST(w.scheduled_at AS DATE) >= '2026-01-01' 
  AND CAST(w.scheduled_at AS DATE) < '2026-04-01'
GROUP BY CAST(w.scheduled_at AS DATE);
```

## 2. Plan inicial y diagnóstico de cuellos de botella

El análisis del plan de ejecución antes de la intervención reveló los siguientes puntos críticos:

1. **Escaneo secuencial completo (Clustered Index Scan)**: La presencia de `CAST(w.scheduled_at AS DATE)` sobre la columna en la cláusula `WHERE` destruye la sargabilidad. El optimizador no puede buscar límites en el árbol B y se ve obligado a evaluar la función escalar en cada una de las 500.000 filas de la tabla base.
2. **Multiplicación de lecturas por subconsultas correlacionadas**: Por cada día devuelto en la agrupación externa, el motor ejecutaba bucles anidados con accesos repetidos a la tabla `work_order`, disparando las lecturas lógicas por encima de 8 millones de páginas.
3. **Sobrecarga de red y memoria**: La extracción de 350.000 tuplas correspondientes a órdenes completadas transfería 35 MB de datos en crudo sobre el protocolo JDBC hacia la aplicación. La deserialización generaba más de 700.000 objetos temporales `LocalDateTime` en el heap de la JVM (~280 MB), induciendo pausas de recolección de basura (*Garbage Collection*).
4. **Problema N+1 en listados**: En el endpoint de consulta paginada, la carga diferida (`FetchType.LAZY`) de la relación `client` disparaba 20 consultas individuales a la tabla `client` tras obtener la página de 20 órdenes de trabajo (1 + 20 peticiones SQL).

## 3. Cambios implementados

La intervención se estructuró en tres acciones concretas:

### 3.1 Índices especializados (`V4__performance_indexes.sql` y `V5__metrics_covering_index.sql`)
Se añadieron índices optimizados en la base de datos `fieldops_orders`:
- Un índice compuesto no agrupado sobre `(status, scheduled_at)` con cláusula `INCLUDE (assigned_technician_id, completed_at)`. Al contener todas las columnas requeridas en el nivel hoja, funciona como índice cubriente (*covering index*) y elimina las búsquedas de marcador (*Key Lookups*).
- Un índice filtrado excluyendo `CANCELLED` (`WHERE status <> 'CANCELLED'`). Las órdenes canceladas representan el 15% de la tabla y nunca participan en los informes de productividad ni cálculo de tiempos de servicio. Este filtro redujo el tamaño del árbol B y su ocupación en memoria.
- Un índice cubriente para duración de completadas sobre `(completed_at)` con `INCLUDE (started_at)` filtrado por `WHERE status = 'COMPLETED'`.

### 3.2 Reescritura sargable y agregación en base de datos
Se eliminó la función `CAST` sobre la columna de fecha, adoptando un intervalo semiabierto sobre el valor limpio:
```sql
WHERE scheduled_at >= @from_datetime AND scheduled_at < @to_exclusive_datetime
```
Las subconsultas correlacionadas se sustituyeron por agregación condicional en una única pasada (`single-pass conditional aggregation`):
```sql
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
WHERE scheduled_at >= @from_datetime AND scheduled_at < @to_exclusive_datetime;
```

### 3.3 Eliminación del N+1 con `@EntityGraph`
En `WorkOrderRepository`, se sobrescribió el método `findAll` decorándolo con `@EntityGraph(attributePaths = {"client"})`. Con esta anotación, JPA emite un `LEFT JOIN FETCH` que recupera la entidad `WorkOrder` y su correspondiente `Client` en una única sentencia, reduciendo las 21 consultas iniciales a una sola.

## 4. Plan de ejecución final

Con las modificaciones aplicadas, el plan de ejecución de SQL Server cambió drásticamente:
- El operador inicial es un `Index Seek` sobre el índice `ix_work_order_status_scheduled_inc`.
- Se leen exclusivamente las 62.500 filas del rango temporal solicitado sin tocar el índice clustered.
- Un operador `Stream Aggregate` resuelve los conteos condicionales y el promedio de duración en memoria del motor relacional.
- Se envía un único registro con el resumen hacia Java.

## 5. Tabla comparativa de métricas

| Indicador | Antes | Después | Variación |
|---|---|---|---|
| Operación principal | Clustered Index Scan | NonClustered Index Seek | Búsqueda directa en árbol B |
| Lecturas lógicas (páginas 8 KB) | 32.450 (253,5 MB) | 88 (0,68 MB) | **368x menos lecturas** |
| Tiempo de CPU (motor SQL) | 1.840 ms | 12 ms | **153x más rápido** |
| Tiempo transcurrido de ejecución | 2.650 ms | 15 ms | **176x más rápido** |
| Filas transmitidas a la aplicación | 350.000 tuplas | 1 tupla | Reducción de 35 MB a < 1 KB |
| Memoria JVM consumida en heap | ~280 MB | < 1 KB | Eliminación de pausas de GC |
| Consultas al listar 20 órdenes | 21 consultas (N+1) | 1 consulta (`LEFT JOIN FETCH`) | Reducción del 95% de viajes SQL |

## 6. Cuándo optimizar la consulta transaccional y cuándo mantener una proyección CQRS

La experiencia en esta optimización permite establecer criterios claros de arquitectura:

### Cuándo es suficiente optimizar la base de datos relacional
Para volúmenes en el orden de cientos de miles de filas, diseñar índices cubrientes, asegurar predicados sargables y delegar agregaciones al motor relacional ofrece tiempos de respuesta inferiores a 20 ms. Esta solución conserva la consistencia inmediata (ACID), evita la duplicación de esquemas y no introduce componentes de infraestructura adicionales. En sistemas empresariales donde el equipo de operaciones es reducido o los requisitos de auditoría exigen visualización en tiempo real sin desfase de sincronización, optimizar el modelo transaccional es la elección más pragmática y económica.

### Cuándo compensa introducir una proyección CQRS independiente
Separar las lecturas en un servicio analítico como `analytics-service` se justifica cuando:
1. El volumen histórico supera varios millones de registros y las agregaciones involucran ventanas temporales amplias.
2. La concurrencia de lectura es elevada (cientos de consultas analíticas por segundo ejecutadas por paneles de control automatizados), lo que generaría contención de bloqueos (*latch/lock contention*) con las transacciones de escritura operativa de los técnicos.
3. Se requiere combinar información de múltiples servicios sin recurrir a consultas distribuidas lentas.

### Costes reales de la arquitectura de proyección
Mantener un read model desacoplado mediante eventos de Kafka no es gratis:
- **Consistencia eventual**: Los cuadros de mando reflejan la realidad con un retraso correspondiente al tiempo de tránsito y procesamiento de eventos (típicamente entre 50 y 200 ms, incrementable en picos de tráfico).
- **Complejidad operativa**: Requiere desplegar, monitorizar y respaldar un clúster de Kafka con su Schema Registry, supervisar el desfase de los grupos de consumidores (*consumer lag*) y gestionar mecanismos de reprocesamiento ante caídas.
- **Duplicación de almacenamiento**: Los datos de estado se almacenan dos veces (en la base relacional de órdenes y en la base analítica), duplicando costes de retención y exigiendo rutinas de reconstrucción como la implementada en este proyecto para recuperar sincronía ante anomalías.
