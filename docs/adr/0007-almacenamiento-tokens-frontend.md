# ADR 0007: Almacenamiento de tokens en el cliente frontend

## Estado
Aceptado

## Contexto
La arquitectura de seguridad de FieldOps implementa autenticación basada en JSON Web Tokens (JWT), con tokens de acceso de corta duración (15 minutos) y tokens de actualización de larga duración (7 días) gestionados por auth-service. La aplicación web de administración (Angular) requiere gestionar el ciclo de vida de estos tokens para autenticar las peticiones dirigidas al API Gateway y mantener la sesión del usuario de forma transparente.

Existen tres alternativas principales para el almacenamiento de credenciales en aplicaciones de página única (SPA):
1. **Cookies HttpOnly con SameSite estricto:** Proporciona protección nativa contra robo mediante Cross-Site Scripting (XSS), pero introduce complejidad en entornos donde el frontend y el gateway residen en dominios o puertos distintos (localhost:4200 vs localhost:8080), y requiere mecanismos adicionales de protección contra falsificación de peticiones en sitios cruzados (CSRF).
2. **Almacenamiento total en Web Storage (localStorage):** Almacenar tanto el token de acceso como el token de actualización en localStorage simplifica la implementación, pero expone el token de acceso directamente a cualquier vulnerabilidad XSS que se introduzca en el cliente.
3. **Estrategia híbrida:** Almacenar el token de acceso exclusivamente en la memoria volátil de la aplicación (mediante señales reactivas de Angular) y persistir únicamente el token de actualización en sessionStorage para limitar su ciclo de vida a la sesión activa del navegador.

## Decisión — Aplicación web-admin (Angular SPA)
Se decide adoptar la estrategia híbrida para la aplicación web-admin:
1. **Token de acceso en memoria:** El token de acceso se almacena de forma privada dentro de una señal reactiva (`signal<string | null>`) en `AuthService`. Nunca se persiste en localStorage, sessionStorage ni cookies de depuración. Al recargar la página, la memoria se vacía.
2. **Token de actualización en sessionStorage:** El token de actualización se almacena bajo una clave específica en `sessionStorage` (no `localStorage`) para limitar su ciclo de vida a la pestaña o ventana activa del navegador. Al cerrar la pestaña el token se elimina automáticamente, reduciendo la superficie de exposición respecto a `localStorage`.
3. **Revocación centralizada:** Al invocar el cierre de sesión, se remueve el token de actualización de sessionStorage, se reinician las señales en memoria y se notifica al endpoint `/api/v1/auth/logout` para invalidar el token en el servidor.

## Decisión — Aplicación móvil de técnico (Ionic/Capacitor)
La aplicación móvil `mobile-technician` presenta un modelo de seguridad diferente al del navegador web:

1. **Almacenamiento seguro nativo mediante `capacitor-secure-storage-plugin`:**
   - En **Android:** El plugin utiliza `EncryptedSharedPreferences` respaldado por Android Keystore, que almacena las claves de cifrado en hardware seguro aislado del proceso de la aplicación.
   - En **iOS:** El plugin utiliza el sistema de **Keychain** del sistema operativo, que proporciona aislamiento por aplicación y cifrado en hardware.
   - Los tres secretos gestionados son: token de acceso, token de actualización y datos del usuario en sesión.

2. **Cifrado de la base de datos SQLite:**
   - La base de datos local (`@capacitor-community/sqlite`) se inicializa con `encrypted=true` y `encryptionMode='encryption'` en dispositivos nativos reales.
   - La passphrase de cifrado se genera la primera vez y se almacena en el almacenamiento seguro nativo (Keychain/Keystore) a través del mismo `capacitor-secure-storage-plugin`.
   - En entornos web/test la base de datos se usa sin cifrado para mantener la compatibilidad con el entorno de pruebas Vitest.

3. **Fallback en entornos web:** En plataformas no nativas (navegador, pruebas unitarias), el servicio `AuthStorageService` utiliza `@capacitor/preferences` como backend alternativo, lo que mantiene la funcionalidad sin necesitar APIs nativas.

## Consecuencias

### Positivas
- **Reducción de la superficie de ataque XSS (web):** El token de acceso en uso no está accesible en el almacenamiento persistente del navegador, impidiendo su extracción pasiva mediante lecturas directas del almacenamiento local.
- **Sesión acotada a la pestaña (web):** El uso de `sessionStorage` en lugar de `localStorage` garantiza que al cerrar el navegador o la pestaña, el token de actualización se destruye automáticamente sin necesidad de lógica adicional.
- **Secretos en hardware seguro (móvil):** Los tokens en la app nativa están protegidos por Keychain o Keystore, que son resistentes a la extracción incluso en dispositivos comprometidos sin acceso a root total.
- **Base de datos cifrada (móvil):** Los datos de órdenes de trabajo almacenados localmente en modo offline están cifrados en reposo, cumpliendo con requisitos de protección de datos empresariales.

### Negativas y limitaciones
- **Exposición residual (web):** Si un atacante ejecuta código JavaScript arbitrario mediante XSS en la misma pestaña activa, podría leer el token de actualización de sessionStorage. Esta limitación se mitiga con la rotación de tokens de actualización y la lista negra de revocación del servidor.
- **Dependencia de plugins nativos (móvil):** En iOS, la disponibilidad de Keychain requiere provisioning correcto. El fallback a `@capacitor/preferences` en entornos no nativos puede reducir la seguridad del entorno de desarrollo si no se gestiona adecuadamente.
- **Petición adicional al recargar (web):** Cada recarga completa requiere una llamada de refresco inicial para reconstruir el estado de autenticación en memoria.

