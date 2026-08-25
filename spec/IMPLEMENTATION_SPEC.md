# MVP de notificaciones — spec de implementación

**Objetivo:** entregar hoy una aplicación Spring Boot demostrable que implemente la API de autoservicio y haga visible una arquitectura hexagonal. El alcance prioriza consistencia de estado, aislamiento por cliente y replay idempotente.

## 1. Fuente de verdad y límites

### Requisitos del enunciado

- Validar mediante una suscripción si un evento debe entregarse.
- Entregarlo por HTTPS a una URL configurable.
- Gestionar reintentos y conservar el resultado final.
- Exponer `GET /notification_events`, `GET /notification_events/{notification_event_id}` y `POST /notification_events/{notification_event_id}/replay`.
- Consultar eventos por cliente y filtrar por fecha de creación y `delivery_status`.
- Usar Java, Spring Boot y arquitectura hexagonal.

### Hechos del fixture `../../../Downloads/notification_events.json`

Cada elemento contiene `event_id`, `event_type`, `content` (texto), `delivery_date` (ISO-8601 UTC), `delivery_status` (`completed` o `failed`) y `client_id`.

El fixture no incluye `event_created_at`, aunque la API debe filtrar por esa fecha. **Para la demo**, el importador copiará `delivery_date` a `event_created_at`; esto es una normalización exclusiva del fixture y queda documentada como limitación.

## 2. Alcance de hoy

### Incluido

- Migración MySQL/Flyway y datos de ejemplo.
- Modelo de dominio y puertos hexagonales.
- Consulta paginada, detalle y replay.
- Transición condicional atómica `FAILED -> PENDING`.
- `WebhookClient` HTTPS configurable.
- `NotificationQueue` mediante adaptador local para demo.
- Tests de contrato y de reglas críticas.
- README con diagrama, decisiones, riesgos OWASP y limitaciones.

### Diferido y documentado

- Adaptador SQS real, DLQ y visibility timeout.
- Scheduler de retries y recovery de trabajos abandonados.
- Circuit breaker, rate limiting global, métricas y trazas distribuidas.
- Autenticación/autorización real. El API mantendrá `clientId` explícito solo para la demo; producción debe derivarlo de la identidad autenticada.

## 3. Arquitectura

```text
REST Controller / Local Queue Listener
             |
             v
       application services
       /       |         \
repository  subscription  webhook / queue ports
   |            |             |
 MySQL         MySQL       HTTP / local queue adapters
```

Paquetes:

```text
com.cobre.notifications
  domain/
  application/port/in/
  application/port/out/
  application/service/
  infrastructure/inbound/rest/
  infrastructure/outbound/persistence/
  infrastructure/outbound/http/
  infrastructure/outbound/queue/
  infrastructure/config/
```

La capa `application` no conoce Spring MVC, JPA, SQS ni `RestClient`.

## 4. Modelo y estados

```text
PENDING -> DELIVERING -> COMPLETED
                       -> RETRY_SCHEDULED -> PENDING
                       -> FAILED
PENDING -> NOT_SUBSCRIBED
PENDING -> SUBSCRIPTION_INACTIVE
FAILED  -> PENDING (replay)
```

`COMPLETED`, `FAILED`, `NOT_SUBSCRIBED` y `SUBSCRIPTION_INACTIVE` son terminales. Para este MVP el retry automático queda documentado, no programado.

`delivery_date` significa fecha de resultado final del delivery y puede existir en `COMPLETED` o `FAILED`, conforme al fixture.

## 5. Persistencia

Tabla `notification_event`:

```text
event_id PK VARCHAR(16)
client_id VARCHAR(16) NOT NULL
event_type VARCHAR(64) NOT NULL
content VARCHAR(512) NOT NULL
event_created_at DATETIME(6) NOT NULL
delivery_date DATETIME(6) NULL
delivery_status ENUM NOT NULL
attempt_count INT UNSIGNED NOT NULL DEFAULT 0
next_retry_at DATETIME(6) NULL
last_error VARCHAR(500) NULL
created_at DATETIME(6) NOT NULL
updated_at DATETIME(6) NOT NULL
```

Tabla `subscription`:

```text
subscription_id BIGINT UNSIGNED PK AUTO_INCREMENT
client_id VARCHAR(16) UNIQUE NOT NULL
status ENUM('ACTIVE', 'INACTIVE') NOT NULL
created_at DATETIME(6) NOT NULL
updated_at DATETIME(6) NOT NULL
```

Índices: `PRIMARY KEY(event_id)`, `(client_id, delivery_status, event_created_at, event_id)`, `(delivery_status, next_retry_at)` y `(delivery_status, updated_at)`.

## 6. Contrato REST

### Listar

```http
GET /notification_events?clientId=CLIENT001&deliveryStatus=FAILED&from=2024-03-01T00:00:00Z&to=2024-03-31T23:59:59Z&page=1&pageSize=20
```

- `clientId`: obligatorio en MVP.
- `deliveryStatus`, `from` y `to`: opcionales.
- `from <= to`; rango máximo de un mes.
- `page`: default `1`, mínimo `1`.
- `pageSize`: default/máximo `20`.
- Orden: `event_created_at DESC, event_id DESC`.
- Respuesta `200`: `{ "events": [...], "page": 1, "pageSize": 20 }`.
- Parámetro inválido: `400`.

### Detalle

```http
GET /notification_events/{notification_event_id}
```

- `200` con el evento.
- `404` si no existe.

### Replay

```http
POST /notification_events/{notification_event_id}/replay
```

- Ejecutar `UPDATE ... WHERE event_id = ? AND delivery_status = 'FAILED'`.
- Si actualiza una fila: `attempt_count = 0`, `next_retry_at = NULL`, estado `PENDING`, publicar en `NotificationQueue`, devolver `202` sin body.
- Si el evento no existe: `404`.
- Si existe pero no está en `FAILED`: `422`.
- La publicación puede duplicarse ante una caída entre DB y cola; se acepta semántica at-least-once y se documenta.

## 7. Puertos mínimos

```java
NotificationEventRepository.findById(...)
NotificationEventRepository.search(...)
NotificationEventRepository.replayIfFailed(...)
NotificationEventRepository.save(...)
SubscriptionRepository.findByClientId(...)
NotificationQueue.publish(...)
WebhookClient.deliver(...)
```

Casos de uso: `GetNotificationEvents`, `GetNotificationEventDetail`, `ReplayNotification`, `ProcessNotificationEvent`.

## 8. Criterios de aceptación

- La app inicia con un único comando documentado.
- El fixture queda visible mediante el endpoint de consulta.
- El filtro por cliente no mezcla eventos de otros clientes.
- El detalle de un ID inexistente devuelve `404`.
- Un `FAILED` puede pasar a `PENDING` una sola vez bajo requests concurrentes.
- Un replay sobre un estado no fallido devuelve `422`.
- La URL HTTPS del webhook no está hardcodeada.
- README declara qué no está implementado y cómo evolucionaría a SQS/retry/recovery reales.

## 9. Demo de panel

1. Levantar la app y consultar eventos de `CLIENT002` con estado `FAILED`.
2. Consultar `EVT003`.
3. Ejecutar replay de `EVT003` y mostrar `202`.
4. Consultar nuevamente el detalle y mostrar `PENDING`.
5. Repetir replay y mostrar `422`.
6. Explicar: transacción condicional, at-least-once, idempotencia por `event_id`, y evoluciones productivas.
