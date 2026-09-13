# ADR 0003: Autenticación con JWT RS256 y distribución de claves públicas mediante JWKS

## Estado
Aceptado

## Contexto
El sistema expone múltiples servicios tras un API Gateway central. Para procesar cada petición HTTP es indispensable verificar la autenticidad de la sesión del usuario, su identificador único y los roles asignados (como `ADMIN` o `TECHNICIAN`) sin ejecutar consultas síncronas a la base de datos de usuarios en cada llamada de red.

El método más elemental de autenticación distribuida consiste en utilizar JSON Web Tokens firmados mediante algoritmos simétricos (HMAC-SHA256 con una clave secreta compartida). Bajo este esquema, cualquier servicio que necesite validar un token debe almacenar en su configuración la misma cadena secreta empleada para generarlo. Si la clave secreta se filtra o un servicio intermedio se ve comprometido, el atacante obtiene la capacidad técnica de falsificar tokens válidos con privilegios administrativos para todo el ecosistema.

## Decisión
Se implementa una arquitectura de criptografía asimétrica basada en el algoritmo RS256 (RSA con SHA-256) y claves de 2048 bits:

1. **Aislamiento de la clave privada:** La clave privada RSA reside exclusivamente en `auth-service`, almacenada en disco o montada vía volumen en formato PEM. Ningún otro servicio del sistema tiene acceso físico ni lógico a esta clave.
2. **Exposición pública de claves:** `auth-service` expone un endpoint estándar en formato JSON Web Key Set (`GET /.well-known/jwks.json`). Este endpoint entrega los parámetros públicos de la clave RSA (`kty`, `alg`, `use`, `n`, `e`) identificados por un identificador de clave (`kid`).
3. **Validación autónoma en el API Gateway:** Spring Cloud Gateway descarga la clave pública desde el endpoint JWKS y la almacena en memoria volátil con expiración configurable. Con esta clave pública, el gateway verifica criptográficamente la firma digital y los reclamos de caducidad (`exp`) e emisor (`iss`) de manera descentralizada, inyectando cabeceras seguras (`X-User-Id`, `X-User-Role`) en las peticiones enrutadas hacia los microservicios internos.

## Consecuencias

### Positivas
- Cumplimiento estricto del principio de menor privilegio. Solo `auth-service` puede firmar y emitir credenciales. La vulneración del gateway o de los servicios de negocio aguas abajo no permite la falsificación de tokens.
- Rotación transparente de credenciales. Se pueden incorporar nuevas claves de firma al JWKS manteniendo la clave anterior activa durante el período de vigencia de los tokens en tránsito, facilitando procedimientos de rotación sin tiempo de inactividad.
- Desacoplamiento total entre servicios. Los microservicios internos no necesitan comunicarse con `auth-service` durante el tráfico ordinario de peticiones de usuario.

### Negativas y limitaciones asumidas
- Mayor coste computacional en la verificación. La verificación de firmas asimétricas RSA con claves de 2048 bits demanda entre tres y cinco veces más operaciones de procesador que un hash HMAC-SHA256 simétrico. En pruebas de carga locales con miles de peticiones concurrentes el impacto sobre la latencia promedio del gateway fue inferior a 1,2 milisegundos.
- Dependencia en el arranque. El API Gateway requiere que `auth-service` esté operativo y entregue su conjunto JWKS para poder validar los tokens entrantes de los clientes.