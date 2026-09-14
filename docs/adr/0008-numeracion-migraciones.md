# ADR 0008: Numeración y secuencia de migraciones Flyway

## Estado
Aceptado

## Contexto
Durante la auditoría técnica de FieldOps (Hallazgo #25), se identificó un salto en la secuencia numérica de las migraciones Flyway de `orders-service`: existían `V1__initial_schema.sql`, `V2__outbox_event.sql`, `V3__shedlock.sql` y `V5__performance_indexes.sql`, omitiéndose la versión `V4`.

Los saltos en la numeración de migraciones introducen ambigüedad operativa: nuevos desarrolladores y administradores de bases de datos pueden sospechar que se omitió o extravió un script crítico de esquema, o que un despliegue previo falló al registrar un artefacto.

Existen dos estrategias para abordar esta discrepancia:
1. **Conservar el hueco e introducir una migración no operativa (NOOP) o documentar la excepción:** Evita alterar checksums en bases de datos existentes de larga duración, pero perpetúa deuda de diseño en un entorno que se inicializa y reproduce desde scripts limpios (`start.sh`).
2. **Renumerar contiguamente y consolidar la secuencia:** Renombrar `V5__performance_indexes.sql` a `V4__performance_indexes.sql`, documentar explícitamente la renumeración en el encabezado del archivo SQL y destinar `V5__metrics_covering_index.sql` a los nuevos índices cubrientes de cálculo de métricas (F3-T09).

## Decisión
Se decide renumerar la secuencia de migraciones de `orders-service` para restablecer una secuencia contigua estricta:
1. **Renombrado a V4:** `V5__performance_indexes.sql` pasa a ser `V4__performance_indexes.sql`, incluyendo un comentario explicativo en el script para trazabilidad histórica.
2. **Asignación de V5:** El nuevo índice cubriente para agregación de métricas de rendimiento (`ix_work_order_completed_duration`) se establece en `V5__metrics_covering_index.sql`.
3. **Regla de contigüidad:** Todas las migraciones futuras en los servicios de FieldOps deben mantener numeración consecutiva estricta (`V1`, `V2`, `V3`, `V4`, `V5`...). No se admiten versiones salteadas. En caso de retirar una migración en entornos productivos ya desplegados, se utilizará una migración compensatoria explícita en lugar de eliminar o saltear versiones.

## Consecuencias

### Positivas
- **Claridad y determinismo:** `flyway_schema_history` refleja una secuencia matemática contigua (1, 2, 3, 4, 5) sin huecos confusos.
- **Trazabilidad:** Cualquier auditor o ingeniero puede verificar la evolución del modelo relacional sin requerir suposiciones sobre migraciones intermedias faltantes.
- **Preparación para CI/CD:** Facilita la validación automatizada de migraciones en pipelines continuos y pruebas con Testcontainers.

### Negativas y limitaciones
- **Incompatibilidad con bases de datos preexistentes no reiniciadas:** Entornos locales muy antiguos que ya hubieran registrado la migración bajo la versión 5 en su tabla `flyway_schema_history` requerirían reiniciar su base de datos o ejecutar `flyway repair`. Dado que el ciclo de vida local se gestiona mediante `docker compose down -v && ./scripts/start.sh`, esta consecuencia queda completamente neutralizada.
