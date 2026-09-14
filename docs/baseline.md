# Línea Base de Pruebas (Post-Remediación)

Fecha de ejecución: 2026-09-13
Entorno: Windows 11, JDK 21.0.8, Node v24.20.0, npm 11.19.0

## Resumen Global

| Módulo / Proyecto | Pasan (Passed) | Fallan (Failed/Errors) | Saltados (Skipped) | Total |
|---|---|---|---|---|
| `backend/api-gateway` | 16 | 0 | 0 | 16 |
| `backend/auth-service` | 27 | 0 | 0 | 27 |
| `backend/orders-service` | 66 | 0 | 0 | 66 |
| `backend/notification-service` | 17 | 0 | 0 | 17 |
| `backend/analytics-service` | 25 | 0 | 0 | 25 |
| **Total Backend** | **151** | **0** | **0** | **151** |
| `frontend/web-admin` | 19 | 0 | 0 | 19 |
| `frontend/mobile-technician` | 20 | 0 | 0 | 20 |
| **Total Frontend** | **39** | **0** | **0** | **39** |
| **TOTAL SISTEMA** | **190** | **0** | **0** | **190** |

## Detalle por Módulo

### 1. `backend/api-gateway`
- `CorsGatewayTest`: 3 passed, 0 failed, 0 skipped
- `GatewayRoutingTest`: 1 passed, 0 failed, 0 skipped
- `RateLimiterTest`: 6 passed, 0 failed, 0 skipped (añadidos tests de cuota por sujeto y proxies confiables)
- `SecurityGatewayTest`: 4 passed, 0 failed, 0 skipped
- `TraceIdFilterTest`: 2 passed, 0 failed, 0 skipped
- **Total**: 16 passed, 0 failed, 0 skipped.
- **JaCoCo**: Cobertura verificada (mínimo 50% en gateway).

### 2. `backend/auth-service`
- `AuthIntegrationTest`: 7 passed, 0 failed, 0 skipped
- `JwksControllerTest`: 1 passed, 0 failed, 0 skipped
- `AuthServiceTest`: 10 passed, 0 failed, 0 skipped (añadidos tests de revocación y blacklist)
- `TokenServiceTest`: 4 passed, 0 failed, 0 skipped
- `UserControllerTest`: 2 passed, 0 failed, 0 skipped (añadidos tests para `GET /api/v1/users/{id}`)
- `AuthApplicationTest`: 1 passed, 0 failed, 0 skipped
- `DemoDataInitializerTest`: 1 passed, 0 failed, 0 skipped
- `RsaKeyProviderTest`: 1 passed, 0 failed, 0 skipped
- `TraceFilterTest`: 2 passed, 0 failed, 0 skipped
- **Total**: 27 passed, 0 failed, 0 skipped.

### 3. `backend/orders-service`
- `WorkOrderControllerTest`: passed (validación `If-Match`, 428 Precondition Required, 409 Conflict)
- `WorkOrderServiceTest`: passed (máquina de estados, auditoría, transiciones)
- `WorkOrderIntegrationTest`: passed (transaccionalidad outbox)
- `EvidenceServiceTest`: passed (almacenamiento y MIME types)
- `ClientControllerTest`: passed
- `OutboxPublisherTest`: passed
- **Total**: 66 passed, 0 failed, 0 skipped.

### 4. `backend/notification-service`
- `NotificationEventListenerTest`: passed (consumo de Kafka y resolución de usuarios vía AuthServiceClient)
- `NotificationProcessingServiceTest`: passed (idempotencia y registro en `notification_log`)
- `NotificationLogWriterTest`: 3 passed, 0 failed, 0 skipped (intentos, enviado y fallos truncados)
- `EmailServiceTest`: passed
- **Total**: 17 passed, 0 failed, 0 skipped.

### 5. `backend/analytics-service`
- `AnalyticsApplicationTest`: 1 passed, 0 failed, 0 skipped
- `AnalyticsControllerTest`: 8 passed, 0 failed, 0 skipped
- `DefaultAnalyticsProjectionServiceTest`: 6 passed, 0 failed, 0 skipped
- `DefaultAnalyticsQueryServiceTest`: 2 passed, 0 failed, 0 skipped
- `DefaultProjectionRebuildServiceTest`: 3 passed, 0 failed, 0 skipped
- `AnalyticsEventListenerTest`: 3 passed, 0 failed, 0 skipped
- `WorkOrderDailyMetricRepositoryTest`: 2 passed, 0 failed, 0 skipped (incluye test de promedio ponderado con 3 eventos de 10, 20 y 60 min -> 30.00)
- **Total**: 25 passed, 0 failed, 0 skipped.

### 6. `frontend/web-admin`
- `src/app/core/interceptors/auth.interceptor.test.ts`: 4 passed
- `src/app/app.test.ts`: 1 passed
- `src/app/core/services/auth.service.test.ts`: 6 passed (sessionStorage)
- `src/app/features/work-orders/work-order-detail/work-order-detail.component.test.ts`: 3 passed (409 conflict banner y botón de recarga)
- `src/app/features/work-orders/work-orders-list/work-orders-list.component.test.ts`: 5 passed
- **Total**: 19 passed, 0 failed, 0 skipped.

### 7. `frontend/mobile-technician`
- `src/app/core/services/network.service.test.ts`: 2 passed
- `src/app/core/services/geolocation.service.test.ts`: 1 passed
- `src/app/core/services/database.service.test.ts`: 4 passed (almacén SQLite y fallback degradado)
- `src/app/core/services/offline-queue.service.test.ts`: 2 passed (cálculo centralizado de versiones)
- `src/app/core/services/camera.service.test.ts`: 1 passed
- `src/app/core/services/sync.service.test.ts`: 3 passed (bloqueo en cascada y retroceso exponencial)
- `src/app/features/orders/order-detail/order-detail.page.test.ts`: 1 passed
- `src/app/features/orders/orders-list/orders-list.page.test.ts`: 1 passed
- `src/app/features/sync/conflict-list/conflict-list.page.test.ts`: 2 passed (pantalla de lista de conflictos)
- `src/app/features/sync/conflict-detail/conflict-detail.page.test.ts`: 3 passed (pantalla de detalle y resolución de conflictos)
- **Total**: 20 passed, 0 failed, 0 skipped.

## Errores Textuales de Tests Rojos
Ninguno. El 100% de los tests del sistema pasan en verde (187 tests en total).
