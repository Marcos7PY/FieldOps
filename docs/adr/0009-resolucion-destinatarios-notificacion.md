# ADR 0009: Resolución desacoplada de destinatarios para notificaciones

## Estado
Aceptado

## Contexto
En el flujo original de creación y transición de órdenes de trabajo, el servicio `orders-service` publicaba eventos en Apache Kafka conteniendo directamente las direcciones de correo electrónico del técnico asignado y del cliente (`technicianEmail`, `clientEmail`). Sin embargo, en la arquitectura de microservicios de FieldOps:
- La información de usuarios (incluyendo técnicos, nombres y correos corporativos) es propiedad exclusiva de `auth-service`.
- La información de clientes reside en `orders-service`.

Esta implementación original provocaba dos anomalías críticas:
1. **Invención o simulación de identidades:** Dado que `orders-service` no poseía el catálogo de usuarios de `auth-service`, generaba correos ficticios mediante patrones sintéticos (`tecnico${id}@fieldops.com`), lo cual impedía entregar notificaciones a las cuentas reales de los empleados.
2. **Exposición de datos de carácter personal (PII) en el log de streaming:** El topic de Kafka `fieldops.work-orders.events` posee una política de retención de mensajes de 720 horas (30 días) para permitir reconstrucciones históricas de proyecciones CQRS y reenganche de consumidores caídos. Almacenar correos electrónicos explícitos en el payload inmutable de Kafka contraviene principios de minimización de datos y dificulta el cumplimiento de normativas de protección de datos (como el derecho al olvido o rectificación según GDPR/LOPD).

## Opciones evaluadas

1. **Enriquecimiento síncrono previo en `orders-service`:**
   Hacer que `orders-service` invoque síncronamente a `auth-service` vía REST antes de publicar el evento en Kafka, para obtener el email real del técnico y colocarlo en el mensaje.
   *Desventaja:* Introduce acoplamiento temporal síncrono en la ruta crítica transaccional de creación y actualización de órdenes; si `auth-service` se encuentra bajo alta carga o experimenta latencia, la modificación de la orden de trabajo se ralentiza o falla. Además, persiste el problema de PII en el log de eventos de Kafka con 30 días de retención.

2. **Resolución desacoplada en `notification-service` (Opción elegida):**
   Los eventos publicados en Kafka contienen únicamente los identificadores opacos de las entidades (`technicianId`, `clientId`, `orderId`). Cuando `notification-service` consume un evento (`WorkOrderAssigned` o `WorkOrderStatusChanged`), resuelve síncronamente los datos de contacto necesarios en el momento del envío:
   - Consulta el endpoint `/api/v1/users/{id}` de `auth-service` para obtener el correo actualizado y el nombre completo del técnico.
   - Si el técnico o su correo no están disponibles, registra la advertencia y cancela el despacho sin afectar la transacción de la orden.
   - Implementa un mecanismo de reintento o caché de corta duración para minimizar la carga sobre `auth-service`.

## Decisión
Se decide adoptar la **Opción 2: Resolución desacoplada en `notification-service`**:
1. El esquema Avro `WorkOrderEvent` transporta identificadores canónicos (`technicianId`, `clientId`) y prescinde de campos redundantes de correo electrónico.
2. `auth-service` expone un endpoint seguro (`GET /api/v1/users/{id}`) accesible internamente o a través de tokens de servicio con el rol adecuado.
3. `notification-service` invoca a `auth-service` mediante un cliente HTTP configurado (`AuthServiceClient`) con timeouts estrictos (2 segundos de conexión, 3 segundos de lectura) y reintentos ante fallos transitorios.
4. El envío de correos electrónicos se genera utilizando los datos recuperados en tiempo de ejecución, asegurando que cualquier cambio reciente en el perfil o correo del usuario se refleje inmediatamente en las notificaciones salientes.

## Consecuencias

### Positivas
- **Desacoplamiento transaccional:** La persistencia de órdenes en `orders-service` no depende de la disponibilidad inmediata de `auth-service`.
- **Privacidad y cumplimiento normativo (PII):** Los datos personales identificables (correos electrónicos) no quedan almacenados indefinidamente en el historial de particiones de Kafka (720 horas de retención).
- **Consistencia y exactitud:** Los correos se dirigen a las direcciones reales y actualizadas gestionadas por el servicio de identidad corporativo.
- **Principio de responsabilidad única:** `orders-service` se enfoca estrictamente en la máquina de estados de las órdenes; `notification-service` asume la responsabilidad de la logística de comunicación y resolución de canales de contacto.

### Negativas y limitaciones asumidas
- **Dependencia de red en `notification-service`:** El consumidor de notificaciones debe comunicarse con `auth-service`. Si `auth-service` está temporalmente inaccesible, el procesamiento del evento en `notification-service` puede experimentar demoras o requerir reintentos con backoff.
- **Llamada de red adicional por notificación:** Cada evento despachado genera una consulta HTTP hacia `auth-service`, lo que incrementa el tráfico interno entre servicios (mitigable en el futuro mediante caché local TTL en memoria para perfiles de técnicos activos).
