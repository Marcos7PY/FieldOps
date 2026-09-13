# ADR 0004: Almacenamiento de tokens en el cliente frontend

## Estado
Aceptado

## Contexto
La arquitectura de seguridad de FieldOps implementa autenticación basada en JSON Web Tokens (JWT), con tokens de acceso de corta duración (15 minutos) y tokens de actualización de larga duración (7 días) gestionados por auth-service. La aplicación web de administración (Angular) requiere gestionar el ciclo de vida de estos tokens para autenticar las peticiones dirigidas al API Gateway y mantener la sesión del usuario de forma transparente.

Existen tres alternativas principales para el almacenamiento de credenciales en aplicaciones de página única (SPA):
1. **Cookies HttpOnly con SameSite estricto:** Proporciona protección nativa contra robo mediante Cross-Site Scripting (XSS), pero introduce complejidad en entornos donde el frontend y el gateway residen en dominios o puertos distintos (localhost:4200 vs localhost:8080), y requiere mecanismos adicionales de protección contra falsificación de peticiones en sitios cruzados (CSRF).
2. **Almacenamiento total en Web Storage (localStorage):** Almacenar tanto el token de acceso como el token de actualización en localStorage simplifica la implementación, pero expone el token de acceso directamente a cualquier vulnerabilidad XSS que se introduzca en el cliente.
3. **Estrategia híbrida:** Almacenar el token de acceso exclusivamente en la memoria volátil de la aplicación (mediante señales reactivas de Angular) y persistir únicamente el token de actualización en localStorage para permitir la restauración de sesión.

## Decisión
Se decide adoptar la estrategia híbrida para la aplicación web-admin:
1. **Token de acceso en memoria:** El token de acceso se almacena de forma privada dentro de una señal reactiva (`signal<string | null>`) en `AuthService`. Nunca se persiste en localStorage, sessionStorage ni cookies de depuración. Al recargar la página, la memoria se vacía.
2. **Token de actualización en localStorage:** El token de actualización se almacena bajo una clave específica en localStorage para permitir que la aplicación renueve el token de acceso al cargar la interfaz o cuando el interceptor HTTP detecte una respuesta 401 Unauthorized.
3. **Revocación centralizada:** Al invocar el cierre de sesión, se remueve el token de actualización de localStorage, se reinician las señales en memoria y se notifica al endpoint `/api/v1/auth/logout` para invalidar el token en el servidor.

## Consecuencias

### Positivas
- **Reducción de la superficie de ataque XSS:** El token de acceso utilizado para las operaciones de negocio no está accesible en el almacenamiento persistente del navegador, impidiendo su extracción pasiva mediante lecturas directas del almacenamiento local.
- **Experiencia de usuario fluida:** La sesión se mantiene a través de recargas de página solicitando un nuevo token de acceso mediante el token de actualización persistido.
- **Desacoplamiento del transporte:** No se requiere la configuración de cookies de terceros ni políticas complejas de SameSite entre orígenes cruzados en despliegues con dominios diferenciados.

### Negativas y limitaciones
- **Exposición residual del token de actualización:** Si un atacante ejecuta código JavaScript arbitrario mediante una vulnerabilidad XSS, podría leer el token de actualización de localStorage. Esta limitación se mitiga en el backend mediante la rotación de tokens de actualización en cada uso, la invalidación en lista negra de revocación y el ciclo de vida limitado del token.
- **Petición adicional al recargar:** Cada recarga completa de la página en el navegador requiere una llamada de refresco inicial para reconstruir el estado de autenticación en memoria.
