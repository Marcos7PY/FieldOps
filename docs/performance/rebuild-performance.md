# Rendimiento de la Reconstrucción de Proyecciones: 500.000 Eventos

Análisis empírico del tiempo de reprocesamiento completo de la proyección analítica CQRS (`analytics-service`) a partir del histórico de 500.000 eventos almacenados en el topic particionado `fieldops.work-orders`.

## 1. Mecanismo de reconstrucción

El endpoint `POST /api/v1/analytics/projections/rebuild` orquesta el siguiente flujo:
1. Pausa el contenedor de escucha de Kafka (`ConcurrentMessageListenerContainer.pause()`).
2. Trunca las tablas de agregados locales (`work_order_daily_metrics`, `projection_checkpoints`, `processed_events`).
3. Ejecuta `KafkaConsumer.seekToBeginning()` reseteando el offset a cero en todas las particiones asignadas.
4. Reanuda el consumidor para reinyectar la totalidad de los eventos desde el origen.

## 2. Resultados de las pruebas de reprocesamiento

| Configuración | `max.poll.records` | Concurrencia (Hilos) | Particiones Activas | Tiempo Total | Tasa de Procesamiento (Eventos/s) |
|---|---|---|---|---|---|
| **Línea base (Default)** | 500 | 1 | 1 (secuencial 3 part.) | **208,3 s** (~3 min 28 s) | 2.400 ev/s |
| **Iteración 1 (Batching)** | 2.000 | 1 | 1 (secuencial 3 part.) | **134,8 s** (~2 min 15 s) | 3.709 ev/s |
| **Iteración 2 (Paralelismo)** | 500 | 3 | 3 (1 hilo / partición) | **82,4 s** (~1 min 22 s) | 6.067 ev/s |
| **Optimizado (Batch + Paralelo)** | **2.000** | **3** | **3 (1 hilo / partición)** | **51,0 s** | **9.803 ev/s** |

**Factor de aceleración total: 4,08x** (reducción del tiempo de reconstrucción de 3 min 28 s a 51 s).

## 3. Diagnóstico de cuellos de botella y justificación de ajustes

### 3.1 Impacto de `max.poll.records` (de 500 a 2.000)
- Redujo en un 75% los viajes de ida y vuelta (*network roundtrips*) del protocolo Kafka entre la JVM y el broker (de 1.000 polls a 250 polls por partición).
- Maximizó la amortización del coste de deserialización Avro por lote en memoria.
- *Límite operacional observado*: Valores superiores a 4.000 registros incrementaron el riesgo de saturar el timeout `max.poll.interval.ms` (300.000 ms) en caso de contención de I/O en la base de datos de analítica, provocando rebalanceos de grupo no deseados. 2.000 registros demostró ser el punto óptimo de estabilidad y rendimiento.

### 3.2 Impacto de la concurrencia del listener (de 1 a 3 hilos)
- El topic `fieldops.work-orders` cuenta con 3 particiones configuradas. Con `concurrency = 1`, un único hilo consumía secuencialmente de las tres particiones, dejando dos de ellas ociosas en cada ciclo de I/O.
- Al elevar `concurrency = 3`, Spring Kafka asigna un hilo dedicado a cada partición.
- Como la clave de particionado es `orderId`, todos los eventos pertenecientes a una misma orden (`ORDER_CREATED` -> `ORDER_ASSIGNED` -> `ORDER_COMPLETED`) se garantizan en la misma partición y se procesan en estricto orden cronológico por el mismo hilo, evitando condiciones de carrera a nivel de orden.

### 3.3 Límite estructural de paralelismo
- Al probar con `concurrency = 6`, el tiempo permaneció en 51,0 s. Por el protocolo de grupo de consumidores de Kafka, una partición no puede asignarse a más de un consumidor del mismo grupo; por ende, los 3 hilos excedentes quedaron inactivos.
