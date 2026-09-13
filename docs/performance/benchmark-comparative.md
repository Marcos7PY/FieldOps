# Comparativa de Rendimiento: Consulta Transaccional vs Proyección CQRS

Prueba de carga comparativa entre la consulta SQL optimizada del servicio transaccional (`GET /api/v1/work-orders/metrics`) y el modelo de lectura pre-calculado del servicio analítico (`GET /api/v1/analytics/metrics/daily`).

## 1. Parámetros del experimento

- **Volumen de datos base**: 500.000 órdenes de trabajo distribuidas en 730 días.
- **Entorno de ejecución**: SQL Server 2022 y Spring Boot 3.3 bajo JDK 21 LTS.
- **Carga de prueba**: 50 peticiones HTTP consecutivas por endpoint con autenticación Bearer JWT previa.
- **Métricas capturadas**: Percentil 50 (mediana), percentil 95, mínimo, máximo y lecturas de E/S.

## 2. Resultados de las 50 peticiones

| Métrica | SQL Optimizado (Transaccional) | Read Model (Proyección CQRS) | Factor de Diferencia |
|---|---|---|---|
| **Endpoint** | `GET /api/v1/work-orders/metrics` | `GET /api/v1/analytics/metrics/daily` | - |
| **Muestras** | 50 peticiones | 50 peticiones | - |
| **Tiempo Mínimo** | 14,2 ms | 3,4 ms | 4,2x más rápido |
| **p50 (Mediana)** | **16,8 ms** | **4,2 ms** | **4,0x más rápido** |
| **p95** | **24,2 ms** | **7,1 ms** | **3,4x más rápido** |
| **Tiempo Máximo** | 29,5 ms | 9,8 ms | 3,0x más rápido |
| **Páginas I/O leídas** | 88 páginas (0,68 MB) | 5 páginas (0,04 MB) | 17,6x menos lecturas |
| **Modelo de Consistencia** | Inmediata (ACID estricto) | Eventual (lag de consumidor ~50-200 ms) | Trade-off de consistencia |

## 3. Distribución de tiempos por petición (ms)

```
SQL Optimizado (Transaccional):
[14, 15, 15, 15, 16, 16, 16, 16, 16, 16, 16, 17, 17, 17, 17, 17, 17, 17, 17, 17,
 17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 19, 19, 19, 20, 20, 21, 21, 22,
 22, 23, 23, 24, 24, 24, 25, 26, 28, 29]

Proyección de Eventos (Read Model CQRS):
[3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6,
 6, 6, 7, 7, 7, 7, 8, 8, 9, 10]
```

## 4. Análisis comparativo y trade-offs

1. **Eficiencia en lecturas lógicas**:
   - La consulta transaccional optimizada recorre 88 páginas mediante el índice cubriente `ix_work_order_status_scheduled_inc`.
   - La proyección CQRS lee únicamente 5 páginas de la tabla `work_order_daily_metric` gracias a que el agregador procesó las mutaciones en segundo plano al llegar los eventos de Kafka.
2. **Aislamiento transaccional**:
   - Trasladar las consultas analíticas a `analytics-service` desacopla completamente el tráfico de visualización y reportería del motor transaccional de órdenes, protegiendo las operaciones críticas de creación y transición de estado de cualquier contención de bloqueos.
3. **Consistencia de datos**:
   - La consulta transaccional refleja mutaciones en el mismo milisegundo de su commit.
   - El modelo de lectura CQRS asume consistencia eventual: el cuadro de mando refleja las métricas con un desfase de procesamiento equivalente al retardo de transmisión en Kafka (50-200 ms en condiciones normales).
