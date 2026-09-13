# ADR 0002: Apache Kafka como bus de eventos y patrón Transactional Outbox

## Estado
Aceptado

## Contexto
El ciclo de vida de una orden de trabajo genera eventos de negocio relevantes para múltiples subsistemas. Cuando una orden se crea, se asigna a un técnico, entra en progreso o se finaliza, se deben disparar comunicaciones por correo electrónico y actualizar las métricas de rendimiento operativo.

Se evaluaron sistemas tradicionales de colas como RabbitMQ o brokers basados en JMS frente a una plataforma de streaming de eventos distribuida como Apache Kafka. Los sistemas de colas orientados a mensajes individuales eliminan el mensaje una vez entregado y reconocido por el consumidor. Este comportamiento dificulta reconstruir el estado analítico desde cero cuando se modifica una fórmula de agregación o cuando se incorpora un nuevo servicio analítico meses después del lanzamiento. Por otra parte, la publicación directa hacia el bus durante la ejecución del controlador HTTP expone al sistema a inconsistencias graves si la conexión de red con el broker falla tras haberse confirmado la transacción en la base de datos relacional.

## Decisión
Se adopta Apache Kafka en modo KRaft (sin ZooKeeper) como columna vertebral de eventos, gobernado por Confluent Schema Registry y esquemas binarios Apache Avro:

1. **Topic unificado con particionamiento determinista:** Todos los eventos del ciclo de vida se publican en el topic principal `fieldops.work-orders.events`, configurado con 3 particiones y un factor de retención de 30 días (2.592.000.000 ms). Se utiliza obligatoriamente el identificador de la orden (`orderId`) como clave de partición. Esto garantiza que todos los eventos relativos a una misma orden aterricen en la misma partición física y sean consumidos en estricto orden cronológico, evitando que una notificación de finalización se procese antes que la de creación.
2. **Patrón Transactional Outbox frente a publicación directa:** En `orders-service`, las mutaciones de la orden y su correspondiente evento de dominio se insertan atómicamente en la misma transacción relacional ACID sobre la base de datos `fieldops_orders`. Un hilo programado en segundo plano (`OutboxPublisher`) consulta los registros no procesados, los serializa en formato Avro y los despacha al broker con acuse de recibo total (`acks=all`). Una vez confirmada la persistencia por Kafka, la fila de la tabla outbox se marca como `PROCESSED`.
3. **Manejo de fallos con Dead Letter Topic (DLT):** Los mensajes que no puedan ser procesados tras tres intentos sucesivos se redirigen automáticamente a `fieldops.work-orders.events-dlt` (1 partición), preservando la continuidad del consumo en las particiones principales sin bloquear a los eventos posteriores.

## Consecuencias

### Positivas
- Capacidad de reconstrucción histórica de proyecciones. El servicio de analítica puede reposicionar sus offsets a cero y reproducir 500.000 eventos históricos para reconstruir la base de datos analítica sin interactuar con la base operativa.
- Desacoplamiento temporal entre consumidores. El grupo de consumo de notificaciones y el de analítica operan a velocidades dispares y con compromisos de entrega independientes sin interferir entre sí.
- Tolerancia a caídas del broker. Si Kafka está temporalmente inaccesible, las órdenes de trabajo continúan registrándose en la base de datos local y se acumulan de forma segura en la tabla outbox hasta que el bus reanude operaciones.

### Negativas y limitaciones asumidas
- Consistencia eventual. Las vistas analíticas y las notificaciones no se actualizan en el mismo milisegundo en que la transacción HTTP finaliza.
- Rigidez de esquemas. Cualquier modificación a los contratos de eventos exige compatibilidad regresiva estricta (`BACKWARD`) validada por Schema Registry, prohibiendo la eliminación directa de campos existentes.
- Carga operativa de la infraestructura. Kafka en modo KRaft y Schema Registry agregan al menos dos contenedores adicionales que consumen recursos significativos de disco y procesador.

### Qué se haría distinto en producción
En un entorno de producción con alta concurrencia se reemplazaría el sondeo periódico de la tabla outbox por un conector de captura de datos de cambio (Change Data Capture) basado en Debezium sobre los logs de transacciones de SQL Server. Esto reduciría la latencia de publicación a menos de 20 milisegundos y eliminaría las consultas de sondeo sobre la base de datos. Asimismo, se optaría por un servicio administrado en la nube como Confluent Cloud o Amazon MSK para transferir las tareas de parcheo, replicación geográfica y balanceo de particiones al proveedor de infraestructura.