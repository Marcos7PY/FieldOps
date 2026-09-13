# ADR 0006: Rate Limiting en memoria con ventana deslizante frente a Redis

## Estado
Aceptado

## Contexto
El sistema FieldOps expone endpoints de autenticación, gestión de órdenes de trabajo y analítica a través de un API Gateway centralizado (Spring Cloud Gateway). Para mitigar ataques de denegación de servicio (DoS), intentos de fuerza bruta en `/api/v1/auth/login` y abusos de cuota por parte de clientes automatizados, es indispensable incorporar una capa de limitación de tasa de peticiones (rate limiting).

La solución canónica en el ecosistema Spring Cloud Gateway recurre a Redis como almacén distribuido mediante `RequestRateLimiterGatewayFilterFactory` y scripts Lua para la implementación de Token Bucket. Sin embargo, para la escala de despliegue actual del proyecto, incorporar un clúster o contenedor de Redis introduce una dependencia operativa adicional, mayor consumo de memoria en los entornos locales y de CI, y un punto de fallo extra en la infraestructura de red.

## Decisión
Se decide implementar un filtro global reactivo en memoria (`SlidingWindowRateLimiterFilter`) dentro de Spring Cloud Gateway, utilizando el algoritmo de ventana deslizante milimétrica (*sliding window log*):

1. **Identificación del cliente:** Se utiliza la dirección IP de origen, extrayendo prioritariamente el encabezado `X-Forwarded-For` para soportar despliegues tras proxies inversos o balanceadores L7, y degradando a la dirección del socket remoto.
2. **Estructura de datos:** Se mantiene un `ConcurrentHashMap` con colas de dos extremos (`ArrayDeque<Long>`) protegidas por sincronización a nivel de cliente, registrando marcas de tiempo en milisegundos.
3. **Comportamiento ante saturación:** Al exceder la capacidad configurada (`fieldops.rate-limiting.capacity`, defecto 100 peticiones por ventana de 60 segundos), el gateway corta inmediatamente el flujo sin reenviar la petición a los microservicios aguas abajo, retornando HTTP 429 Too Many Requests con cuerpo en formato RFC 7807 (`ProblemDetail`) y encabezados estándar:
   - `X-RateLimit-Limit`: Capacidad máxima de la ventana.
   - `X-RateLimit-Remaining`: Peticiones restantes en la ventana actual.
   - `Retry-After`: Segundos restantes hasta que expire la petición más antigua del cliente.

## Consecuencias

### Positivas
- **Cero dependencias externas:** No requiere desplegar, configurar ni monitorear instancias de Redis o Valkey, simplificando la orquestación en Docker Compose y reduciendo el consumo de RAM global.
- **Latencia mínima:** Las operaciones de lectura y descarte ocurren en memoria en el orden de microsegundos, eliminando los viajes de ida y vuelta de red (RTT) inherentes a Redis.
- **Precisión temporal superior:** A diferencia de los contadores de ventana fija (*fixed window*) que permiten duplicar la tasa permitida en los bordes de la ventana (efecto frontera), la ventana deslizante garantiza que en cualquier intervalo móvil de N segundos nunca se superen las peticiones parametrizadas.

### Negativas y limitaciones
- **Falta de estado compartido entre réplicas:** Si el API Gateway se escala horizontalmente a múltiples réplicas sin afinidad de sesión (*sticky sessions*), el límite efectivo global para un cliente se multiplica por el número de instancias activas detrás del balanceador de carga.
- **Uso de memoria local:** Cada cliente activo retiene una colección de marcas de tiempo en memoria. En escenarios de ataques masivos distribuidos (DDoS con millones de IPs distintas), la memoria del gateway podría crecer significativamente si no se ejecutan rutinas periódicas de desalojo de clientes inactivos.
- **Migración futura:** Si el sistema escala a un clúster multi-nodo de alta disponibilidad en producción corporativa, esta implementación deberá reemplazarse por un limitador respaldado en Redis distribuido o delegarse en una capa perimetral (Cloudflare, AWS WAF o Envoy).
