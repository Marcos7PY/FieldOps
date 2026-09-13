# ADR 0004: Sincronización sin conexión, cola de operaciones y resolución de conflictos

## Estado
Aceptado

## Contexto
Los técnicos que utilizan la aplicación móvil de FieldOps desempeñan su labor en entornos con cobertura móvil intermitente o nula, como plantas subterráneas, salas de máquinas blindadas y zonas rurales. La aplicación debe permitirles consultar los detalles de las órdenes asignadas, iniciar los trabajos, adjuntar fotografías de evidencia con coordenadas geográficas y registrar el cierre técnico sin depender de una conexión activa a internet.

Si la aplicación bloquease las acciones en ausencia de red, la operatividad del personal de campo se detendría. Por el contrario, permitir modificaciones locales no coordinadas crea el riesgo de que dos partes alteren la misma orden en paralelo. Por ejemplo, un supervisor en la sede central podría reasignar o cancelar una orden mientras el técnico en campo está ejecutándola en modo desconectado.

## Decisión
Se implementa una arquitectura orientada a la sincronización diferida apoyada en una base de datos local embebida y control de concurrencia optimista:

1. **Almacén local transaccional con SQLite:** En la aplicación Ionic se integra `@capacitor-community/sqlite` para mantener réplicas locales de las órdenes asignadas y una tabla de auditoría operativa denominada `pending_operation`. Cada acción ejecutada sin conexión (cambio de estado o carga de evidencia) se guarda de inmediato en la base local y se encola con estado `PENDING` y marca de tiempo milimétrica.
2. **Procesamiento estrictamente secuencial (FIFO):** Cuando el complemento `@capacitor/network` detecta el retorno de la conexión a internet, el motor de sincronización (`SyncService`) despierta y procesa los elementos pendientes en estricto orden cronológico. Esto garantiza que la transición hacia `IN_PROGRESS` se envíe al servidor antes que la transición hacia `COMPLETED`.
3. **Bloqueo optimista con cabecera de versión:** Cada orden de trabajo contiene un atributo de versión incremental (`rowVersion`) gestionado por el motor relacional en `orders-service`. Las peticiones de sincronización envían obligatoriamente la cabecera estándar `If-Match` con el valor de versión capturado cuando el técnico leyó los datos por última vez.
4. **Política de resolución de conflictos:**
   - Si la versión en el servidor coincide con la cabecera `If-Match`, el servidor procesa la transición y responde HTTP 200 OK actualizando la versión.
   - Si un supervisor modificó la orden en el servidor durante el período de desconexión, el servidor rechaza la petición con HTTP 412 Precondition Failed o HTTP 409 Conflict.
   - Ante este fallo, la aplicación no sobreescribe ciegamente los datos del servidor bajo la premisa de que el último en escribir gana ("last write wins"), ya que esto destruiría las decisiones de despacho tomadas en la oficina. La operación local se marca con estado `CONFLICT`, conservando el contenido y la evidencia local intactos, y se alerta al técnico para que decida conscientemente si forzar o descartar la operación.

## Consecuencias

### Positivas
- Disponibilidad operativa ininterrumpida. El técnico registra sus tareas sin demoras perceptibles independientemente de la calidad de la señal celular.
- Preservación de la integridad del negocio. Se eliminan las sobreescrituras accidentales de estados cuando ocurren ediciones concurrentes entre el personal de campo y los coordinadores de despacho.
- Trazabilidad y no pérdida de evidencia fotográfica. Las imágenes capturadas con la cámara se almacenan en el sistema de archivos nativo del dispositivo y no se eliminan hasta que el servidor confirma su recepción exitosa.

### Negativas y limitaciones asumidas
- Mayor complejidad en el código del cliente móvil. La lógica debe gestionar estados de reintento transitorio frente a fallos deterministas de concurrencia.
- Esfuerzo cognitivo adicional para el usuario. Cuando surge un conflicto de versión, el técnico debe interactuar con un diálogo de resolución en lugar de experimentar una sincronización totalmente transparente.