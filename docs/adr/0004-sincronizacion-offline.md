# ADR 0004: Sincronización sin conexión, cola de operaciones y resolución de conflictos

## Estado
Aceptado (actualizado tras fase de remediación F4)

## Contexto
Los técnicos que utilizan la aplicación móvil de FieldOps desempeñan su labor en entornos con cobertura móvil intermitente o nula, como plantas subterráneas, salas de máquinas blindadas y zonas rurales. La aplicación debe permitirles consultar los detalles de las órdenes asignadas, iniciar los trabajos, adjuntar fotografías de evidencia con coordenadas geográficas y registrar el cierre técnico sin depender de una conexión activa a internet.

Si la aplicación bloquease las acciones en ausencia de red, la operatividad del personal de campo se detendría. Por el contrario, permitir modificaciones locales no coordinadas crea el riesgo de que dos partes alteren la misma orden en paralelo. Por ejemplo, un supervisor en la sede central podría reasignar o cancelar una orden mientras el técnico en campo está ejecutándola en modo desconectado.

## Decisión
Se implementa una arquitectura orientada a la sincronización diferida apoyada en una base de datos local embebida, almacenamiento en sistema de archivos nativo y control de concurrencia optimista:

1. **Almacén local transaccional con SQLite y modo degradado en memoria:**
   En la aplicación Ionic se integra `@capacitor-community/sqlite` (`src/app/core/services/database.service.ts`) para mantener réplicas locales de las órdenes asignadas (`local_work_order`) y una tabla de auditoría operativa (`pending_operation`).
   - En dispositivos nativos reales (Android/iOS), la base de datos se inicializa cifrada con `encryptionMode='encryption'`.
   - Si la inicialización de SQLite falla en un dispositivo físico, el sistema cambia su estado a `storageMode = 'failed'` y bloquea las operaciones de escritura para evitar pérdida de datos (`src/app/app.component.ts`).
   - En entornos de desarrollo web o pruebas automatizadas, se utiliza un almacén en memoria reactivo (`Map<number, LocalWorkOrder>`) para garantizar el funcionamiento continuo de los tests.

2. **Almacenamiento de evidencias en sistema de archivos nativo:**
   Las fotografías capturadas no se conservan en memoria ni en cadenas base64 dentro de la base de datos SQLite para evitar problemas de fragmentación y límites de memoria. El servicio `EvidenceStorageService` (`src/app/core/services/evidence-storage.service.ts`) persiste los archivos binarios en el directorio de datos de `@capacitor/filesystem`. La cola almacena únicamente la ruta local (`file_path`). El archivo se lee en forma de Blob/FormData durante la sincronización y se elimina del disco únicamente tras recibir confirmación HTTP 201 Created del backend (`src/app/core/services/sync.service.ts`).

3. **Bloqueo optimista con versión incremental JPA:**
   Cada orden de trabajo contiene un atributo de versión incremental (`version` de tipo `BIGINT`) gestionado a nivel de aplicación mediante la anotación `@Version` de JPA (`backend/orders-service/.../WorkOrderEntity.java`), en lugar de una columna `rowversion` dependiente del motor SQL Server.
   - Toda petición de modificación (`PATCH /status`, `PATCH /assign`) exige la cabecera estándar `If-Match: "<version>"`.
   - Si la versión coincide, el servidor actualiza la entidad, incrementa la versión atómicamente y responde HTTP 200 OK con la cabecera `ETag: "<nueva-version>"`.
   - Si la orden fue modificada concurrentemente por otro usuario o por el supervisor, el servidor rechaza la operación con HTTP 412 Precondition Failed o HTTP 409 Conflict.

4. **Cálculo centralizado de versiones y bloqueo en cascada:**
   - La cola de sincronización calcula centralizadamente la versión esperada para cada operación encadenada (`src/app/core/services/offline-queue.service.ts`). Cuando se encolan múltiples cambios sobre la misma orden sin conexión (por ejemplo, `ASSIGNED -> IN_PROGRESS` y posteriormente `IN_PROGRESS -> COMPLETED`), cada operación incrementa virtualmente la versión esperada respecto a la anterior.
   - Si la primera operación falla con 409 o 412, dicha operación pasa a estado `CONFLICT_MANUAL_REVIEW`. Las operaciones subsecuentes de la misma orden se marcan inmediatamente como `BLOCKED_BY_CONFLICT` (`src/app/core/services/sync.service.ts`), impidiendo el envío de estados inconsistentes al servidor.

5. **Política de reintentos con retroceso exponencial y fluctuación (Jitter):**
   Para errores transitorios de red o caídas del servidor (HTTP 5xx o timeout), `SyncService` (`src/app/core/services/sync.service.ts`) implementa un límite de reintentos (`MAX_RETRIES = 8`).
   - El intervalo de reintento crece exponencialmente: `interval = base * 2^retries` con un factor de aleatoriedad (jitter ±20%) para mitigar tormentas de peticiones concurrentes.
   - La fecha del próximo intento se persiste en la columna `next_attempt_at` de SQLite. La cola ignora las operaciones hasta que venza dicho plazo.
   - Al alcanzar los 8 reintentos sin éxito, la operación se clasifica como `FAILED_PERMANENT` y requiere revisión manual.

6. **Interfaz de usuario para resolución de conflictos:**
   Ante un conflicto de versión o fallo permanente, la aplicación no sobreescribe ciegamente el servidor ("last write wins").
   - El componente de lista de órdenes (`src/app/features/orders/orders-list/orders-list.page.ts`) muestra un distintivo numérico de conflictos pendientes.
   - La pantalla de conflictos (`src/app/features/sync/conflict-list/conflict-list.page.ts` y `src/app/features/sync/conflict-detail/conflict-detail.page.ts`) presenta una comparación lado a lado entre los datos locales encolados y el estado actual del servidor.
   - El técnico dispone de opciones explícitas: reintentar con la versión actual (si la transición es válida en el nuevo estado del servidor) o descartar la operación local para sincronizar los datos del servidor.

## Consecuencias

### Positivas
- **Disponibilidad y resistencia:** El técnico trabaja sin fricciones ni retrasos ante pérdida de señal celular (`src/app/features/orders/order-detail/order-detail.page.ts`).
- **Integridad de datos estricta:** No existen sobreescrituras accidentales gracias a la combinación de `@Version` en el backend y `If-Match` en el cliente.
- **Uso eficiente de recursos móviles:** Las fotografías se transmiten como streams multipart desde el sistema de archivos nativo, evitando desbordamientos de memoria en SQLite.
- **Protección contra cascadas de fallos:** El bloqueo en cascada y los reintentos con jitter previenen la saturación del servidor y estados corruptos en la cola.

### Negativas y limitaciones asumidas
- **Intervención del usuario en casos conflictivos:** Cuando una orden fue reasignada o cancelada por el supervisor mientras el técnico trabajaba sin red, se requiere que el técnico revise el conflicto manualmente a través de la interfaz de resolución.