# ADR 0006: Rate Limiting en memoria con ventana deslizante frente a Redis

## Estado
Aceptado (actualizado tras fase de remediación F2)

## Contexto
El sistema FieldOps expone endpoints de autenticación, gestión de órdenes de trabajo y analítica a través de un API Gateway centralizado (Spring Cloud Gateway). Para mitigar ataques de denegación de servicio (DoS), intentos de fuerza bruta en `/api/v1/auth/login` y abusos de cuota por parte de clientes automatizados, es indispensable incorporar una capa de limitación de tasa de peticiones (rate limiting).

La solución canónica en el ecosistema Spring Cloud Gateway recurre a Redis como almacén distribuido mediante `RequestRateLimiterGatewayFilterFactory` y scripts Lua para la implementación de Token Bucket. Sin embargo, para la escala de despliegue actual del proyecto, incorporar un clúster o contenedor de Redis introduce una dependencia operativa adicional, mayor consumo de memoria en los entornos locales y de CI, y un punto de fallo extra en la infraestructura de red.

## Decisión
Se implementa un filtro global reactivo en memoria (`SlidingWindowRateLimiterFilter`) dentro de Spring Cloud Gateway (`backend/api-gateway/.../SlidingWindowRateLimiterFilter.java`), utilizando el algoritmo de ventana deslizante milimétrica (*sliding window log*):

1. **Resolución segura de identidad del cliente:**
   - **Confianza condicionada en `X-Forwarded-For`:** Para evitar suplantación de IP (*IP spoofing*) mediante cabeceras manipuladas por el cliente, el gateway únicamente confía en `X-Forwarded-For` si la conexión remota inmediata proviene de un proxy inverso o balanceador explícitamente configurado en la lista blanca de confianza (`fieldops.security.trusted-proxies`). Si la conexión proviene de un origen no confiable, se utiliza estrictamente la dirección del socket TCP remoto.
   - **Cuota por sujeto autenticado:** Para peticiones que incluyen un token JWT válido, la clave de cuota se asocia preferentemente al identificador de usuario (`sub`) en lugar de la IP. Esto evita que múltiples técnicos compartiendo la misma red corporativa o NAT móvil se agoten la cuota entre sí, e impide que un usuario malicioso eluda el límite rotando de red celular.

2. **Estructura de datos y desalojo automático:**
   - Se mantiene un `ConcurrentHashMap` con colas de marcas de tiempo milimétricas (`ArrayDeque<Long>`) protegidas por bloqueos de granularidad fina por cliente.
   - **Rutina periódica de desalojo:** Para mitigar la fuga de memoria provocada por IPs efímeras o ataques DDoS con direcciones dispares, un ejecutor programado (`@Scheduled(fixedDelay = 60000)`) purga periódicamente las entradas cuya última actividad supera el doble de la ventana configurada. Si el mapa supera el umbral de capacidad (`fieldops.rate-limiting.max-tracked-keys`), se activa una recolección forzada en el hilo de limpieza.

3. **Comportamiento ante saturación:** Al exceder la capacidad configurada (`fieldops.rate-limiting.capacity`, defecto 100 peticiones por ventana de 60 segundos), el gateway corta inmediatamente el flujo sin reenviar la petición a los microservicios aguas abajo, retornando HTTP 429 Too Many Requests con cuerpo en formato RFC 7807 (`ProblemDetail`) y encabezados estándar:
   - `X-RateLimit-Limit`: Capacidad máxima de la ventana.
   - `X-RateLimit-Remaining`: Peticiones restantes en la ventana actual.
   - `Retry-After`: Segundos restantes hasta que expire la petición más antigua del cliente.

## Consecuencias

### Positivas
- **Cero dependencias externas:** No requiere desplegar, configurar ni monitorear instancias de Redis o Valkey, simplificando la orquestación en Docker Compose y reduciendo el consumo de RAM global.
- **Resistencia contra suplantación:** La validación de proxies confiables impide que atacantes eludan las restricciones inyectando cabeceras `X-Forwarded-For` arbitrarias.
- **Equidad para usuarios legítimos:** El rate limiting basado en sujeto autenticado protege a usuarios que navegan desde una misma dirección IP pública (por ejemplo, oficina central o sede técnica).
- **Memoria acotada:** La rutina de desalojo periódico previene fugas de memoria prolongadas en el gateway.
- **Precisión temporal superior:** La ventana deslizante garantiza que en cualquier intervalo móvil de N segundos nunca se superen las peticiones parametrizadas, neutralizando los picos de tráfico en bordes de ventana fija.

### Negativas y limitaciones
- **Falta de estado compartido entre réplicas independientes:** Si el API Gateway se escala horizontalmente a múltiples réplicas sin afinidad de sesión (*sticky sessions*), el límite efectivo global para un cliente se multiplica por el número de instancias activas detrás del balanceador L4/L7.
- **Migración futura:** Si el sistema escala a un clúster multi-nodo de alta disponibilidad en producción corporativa, esta implementación deberá reemplazarse por un limitador respaldado en Redis distribuido o delegarse en una capa perimetral (Cloudflare, AWS WAF o Envoy).
