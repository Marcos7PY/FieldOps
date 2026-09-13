# ADR 0001: Descomposición en cinco microservicios y límites de agregación

## Estado
Aceptado

## Contexto
FieldOps gestiona el ciclo operativo de órdenes de servicio técnico en campo. La plataforma comprende autenticación y control de identidades, gestión de órdenes de trabajo con captura de evidencias, notificaciones transaccionales por correo electrónico y agregación analítica de productividad.

Durante la etapa de diseño preliminar se evaluaron dos caminos opuestos. La primera alternativa era un monolito modular único en Spring Boot. La segunda era una fragmentación exhaustiva en ocho o diez microservicios independientes, aislando servicios de clientes, técnicos, archivos de evidencia, auditoría, despachos y métricas en unidades de despliegue separadas.

El monolito modular presentaba acoplamiento en tiempo de ejecución. Una saturación en el recálculo analítico de métricas o una lentitud transitoria en el servidor SMTP de correo degradaría el hilo de ejecución que registra las órdenes de trabajo urgentes. Por otro lado, dividir la arquitectura en diez servicios multiplicaba la latencia de red en cada operación básica y demandaba implementar patrones de transacción distribuida (Sagas) para validar la existencia de un técnico o un cliente durante la creación de una orden.

## Decisión
Se establece una división en exactamente cinco servicios backend:

1. **api-gateway:** Punto de entrada único perimetral construido con Spring Cloud Gateway. Valida tokens JWT contra las claves públicas del servicio de autenticación, aplica limitación de tasa de peticiones y enruta el tráfico hacia los servicios internos sin exponerlos a internet.
2. **auth-service:** Administra identidades, usuarios, roles y sesiones. Centraliza la firma asimétrica de tokens mediante claves RSA privadas y expone el conjunto de claves públicas.
3. **orders-service:** Núcleo transaccional del negocio. Gestiona clientes, técnicos, ciclo de vida de órdenes, historial de transiciones, persistencia de evidencias y almacenamiento de eventos en la tabla outbox.
4. **notification-service:** Consumidor asíncrono especializado. Escucha eventos de dominio en Kafka y despacha correos a través de SMTP garantizando idempotencia en los envíos.
5. **analytics-service:** Modelo de lectura CQRS. Proyecta eventos históricos en tablas analíticas dedicadas para responder consultas de rendimiento sin bloquear la base de datos operativa.

Se descarta una mayor granularidad. Las entidades de clientes y técnicos permanecen dentro de `orders-service` porque su ciclo de vida y sus validaciones están estrechamente ligados a la asignación de las órdenes de trabajo. Extraerlas a servicios autónomos obligaría a introducir consultas HTTP remotas en operaciones transaccionales de alta frecuencia. Del mismo modo, las evidencias fotográficas se gestionan dentro de `orders-service` para asegurar que el registro de una imagen mantenga coherencia transaccional con el estado de la orden asociada.

## Consecuencias

### Positivas
- Aislamiento de fallos entre la ingestión de órdenes y las tareas secundarias. La caída del servidor de correo o la saturación del procesamiento analítico no interrumpe el registro ni la actualización de órdenes de trabajo.
- Despliegue y escalabilidad independiente. Los servicios con mayor consumo de cómputo en segundo plano, como el consumidor de eventos analíticos, pueden escalar sus instancias o hilos de procesamiento sin afectar a los servicios transaccionales.
- Fronteras de datos bien delimitadas. Cada microservicio es dueño exclusivo de su esquema de base de datos (`fieldops_auth`, `fieldops_orders`, `fieldops_notifications` y `fieldops_analytics`), eliminando dependencias de tablas compartidas.

### Negativas y limitaciones asumidas
- Huella de memoria considerable en entornos de desarrollo y pruebas. Levantar cinco procesos de máquina virtual Java requiere al menos 2 GB de memoria RAM en reposo antes de atender tráfico.
- Consistencia eventual entre la confirmación de una orden y su disponibilidad en los paneles de analítica.
- Complejidad en la orquestación local y de integración continua, requiriendo contenedores de SQL Server, Kafka y herramientas auxiliares para ejecutar la suite completa de pruebas.