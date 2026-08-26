# Notification Delivery Service — MVP Specification

## Iteración actual: sin consumo inicial desde SQS Standard

## 1. Objetivo

Implementar un servicio de notificaciones en Java y Spring Boot, con arquitectura hexagonal, que permita:

- persistir eventos de notificación;
- validar que el cliente tenga una suscripción activa;
- entregar notificaciones mediante HTTPS a un webhook configurable;
- manejar errores temporales mediante retries;
- recuperar entregas interrumpidas;
- consultar eventos mediante una API REST;
- ejecutar replay de entregas definitivamente fallidas;
- ofrecer observabilidad suficiente para operación y troubleshooting.

> **Exclusión explícita:** el consumo inicial del evento completo desde **SQS Standard no forma parte de esta iteración**. Se incorporará en la última iteración sin modificar el dominio ni los casos de uso existentes.

## 2. Alcance

### 2.1 Incluido

```text
Persistencia de NotificationEvent
            ↓
Validación de Subscription
            ↓
Atomic claim
            ↓
Delivery HTTPS al webhook
            ↓
    ┌───────────────┬──────────────────┐
    │               │                  │
   2xx       error retryable    error definitivo
    │               │                  │
    ▼               ▼                  ▼
COMPLETED     RETRY_SCHEDULED         FAILED
                    │
                    ▼
                Scheduler
                    │
                    ▼
                 PENDING
```

También se incluyen:

```http
GET  /notification_events
GET  /notification_events/{eventId}
POST /notification_events/{eventId}/replay
```

### 2.2 Fuera de alcance

- consumo inicial desde SQS Standard;
- endpoint para crear eventos;
- CRUD de suscripciones;
- webhook diferente por suscripción;
- exactly-once delivery;
- SQS FIFO y garantías de orden;
- transactional outbox;
- autoscaling productivo;
- rate limiter o circuit breaker distribuido;
- historial completo de intentos en una tabla independiente;
- autenticación y autorización productivas completas;
- infraestructura AWS productiva completa.

En esta iteración los eventos pueden ingresar mediante fixture, bootstrap, tests o persistencia directa para facilitar la ejecución. Este mecanismo temporal no debe filtrarse al dominio.

## 3. Arquitectura hexagonal

```text
                 ┌────────────────────────┐
                 │ Inbound adapters       │
                 │ REST · Scheduler       │
                 └───────────┬────────────┘
                             │
                             ▼
                 ┌────────────────────────┐
                 │ Application use cases  │
                 └───────────┬────────────┘
                             │
              ┌──────────────┼───────────────┐
              ▼              ▼               ▼
      NotificationEvent  Subscription     Webhook
        Repository Port  Repository Port  Client Port
              │              │               │
              ▼              ▼               ▼
            MySQL          MySQL        HTTPS Adapter
```

Estructura sugerida:

```text
domain/
  model/
  enums/
application/
  usecase/
  port/
    in/
    out/
infrastructure/
  inbound/
    rest/
    scheduler/
  outbound/
    persistence/
    webhook/
configuration/
```

La capa de dominio y aplicación no debe depender directamente de Spring Data JPA, MySQL, WebClient/RestClient ni detalles HTTP.

Ports de salida mínimos:

- `NotificationEventRepository`
- `SubscriptionRepository`
- `WebhookClient`
- `NotificationQueue` — contrato preparado para la iteración SQS; puede tener una implementación local temporal.

Casos de uso mínimos:

- `ProcessNotificationUseCase`
- `GetNotificationEventsUseCase`
- `GetNotificationEventUseCase`
- `ReplayNotificationUseCase`
- `ScheduleRetriesUseCase`
- `RecoverAbandonedDeliveriesUseCase`

## 4. Modelo `NotificationEvent`

| Campo | Regla |
|---|---|
| `eventId` | Identificador único, estable y PK; mecanismo principal de idempotencia. |
| `clientId` | Cliente propietario del evento. |
| `eventType` | Tipo del evento de negocio. |
| `content` | Texto del evento; se persiste como `VARCHAR`, no como JSON. |
| `eventCreatedAt` | Fecha UTC en que el productor generó el evento. |
| `deliveryDate` | Timestamp provisto/asociado al resultado; no implica exclusivamente éxito. |
| `deliveryStatus` | Estado operativo actual. |
| `attemptCount` | Cantidad de intentos HTTP realizados en el ciclo actual. |
| `nextRetryAt` | Fecha UTC desde la cual un retry queda habilitado. |
| `lastError` | Último error resumido y sanitizado. |
| `createdAt` | Fecha UTC de persistencia en este servicio. |
| `updatedAt` | Última modificación relevante; se usa también para recovery. |

### 4.1 Brecha del fixture

El fixture suministrado no contiene `event_created_at`, aunque la API requiere filtrar por fecha de creación del evento.

- En producción, el productor debe enviar `event_created_at`.
- Para la demo, el loader puede copiar `delivery_date` a `event_created_at`.
- Esta adaptación debe quedar documentada y no representa equivalencia semántica entre ambos campos.

## 5. Estados y máquina de estados

```java
PENDING
DELIVERING
RETRY_SCHEDULED
COMPLETED
FAILED
NOT_SUBSCRIBED
SUBSCRIPTION_INACTIVE
```

```text
PENDING
  └─ atomic claim → DELIVERING
       ├─ 2xx → COMPLETED
       ├─ error retryable y quedan intentos → RETRY_SCHEDULED
       ├─ error definitivo o intentos agotados → FAILED
       ├─ suscripción inexistente → NOT_SUBSCRIBED
       └─ suscripción inactiva → SUBSCRIPTION_INACTIVE

RETRY_SCHEDULED
  └─ retry vencido → PENDING

FAILED
  └─ replay → PENDING
```

Las transiciones deben ser explícitas. Ningún adapter debe modificar estados al margen de un caso de uso.

## 6. Atomic claim

Antes de ejecutar el webhook se debe reclamar el evento mediante una transición atómica:

```text
PENDING → DELIVERING
```

Ejemplo conceptual:

```sql
UPDATE notification_event
SET delivery_status = 'DELIVERING',
    attempt_count = attempt_count + 1,
    updated_at = UTC_TIMESTAMP(6)
WHERE event_id = :event_id
  AND delivery_status = 'PENDING';
```

- Una fila afectada: el proceso obtuvo el claim y puede invocar el webhook.
- Cero filas afectadas: no obtuvo el claim y debe terminar sin invocar el webhook.

Está prohibido implementar el claim como `SELECT`, validación en memoria y luego `UPDATE`; ese patrón `check-then-act` permite entregas concurrentes evitables.

## 7. Subscription

Modelo:

| Campo | Regla |
|---|---|
| `subscriptionId` | PK. |
| `clientId` | Único; una suscripción por cliente en el MVP. |
| `status` | `ACTIVE` o `INACTIVE`. |
| `createdAt` | Timestamp UTC. |
| `updatedAt` | Timestamp UTC. |

Reglas:

- una suscripción inexistente produce `NOT_SUBSCRIBED`;
- una suscripción `INACTIVE` produce `SUBSCRIPTION_INACTIVE`;
- no se implementa CRUD; los datos se cargan manualmente;
- no se define FK desde `notification_event.client_id`, porque debe poder persistirse un evento cuyo cliente no esté suscrito;
- la URL del webhook no pertenece a `Subscription` en este MVP.

## 8. Webhook

La URL es configuración global y reemplazable mediante variable de entorno:

```yaml
webhook:
  url: ${WEBHOOK_URL}
```

Contrato conceptual:

```java
public interface WebhookClient {
    WebhookResult send(NotificationEvent event);
}
```

Requisitos:

- usar HTTPS;
- configurar connect timeout y read timeout;
- desactivar redirects automáticos;
- limitar concurrencia por instancia mediante configuración;
- no incluir secretos ni campos operativos en el payload;
- sanitizar errores y límites de cuerpo antes de persistir o loguear.

## 9. Payload del webhook

```json
{
  "event_id": "EVT003",
  "event_type": "credit_transfer",
  "content": "Bank transfer received from Account #4567 for $1,500.00",
  "client_id": "CLIENT002"
}
```

No se deben enviar `attemptCount`, `lastError`, `nextRetryAt`, `deliveryStatus`, `createdAt` ni `updatedAt`.

El mismo `event_id` debe utilizarse durante todos los intentos y replays para permitir idempotencia en el consumidor.

## 10. Manejo de respuestas HTTP

### 10.1 Éxito

Cualquier respuesta `2xx` produce `COMPLETED`. Se actualizan `deliveryStatus`, `deliveryDate` y `updatedAt`.

### 10.2 Error definitivo

Son no retryable:

- respuestas `3xx`;
- `400`, `401`, `403`, `404`;
- otros `4xx`, excepto `408` y `429`.

El resultado es `FAILED` y se guarda un `lastError` sanitizado.

### 10.3 Error retryable

Son retryable:

- `408`, `429`, `500`, `502`, `503`, `504`;
- connect/read timeout;
- error de conexión o red incierta.

Si `attemptCount < maxAttempts`, el resultado es `RETRY_SCHEDULED`. Si los intentos se agotaron, el resultado es `FAILED`.

Para `429`, se debe respetar un `Retry-After` válido. Si falta o es inválido, se utiliza la política local de backoff.

## 11. Retry policy

Se utiliza exponential backoff con jitter, acotado por `maxDelay`.

```yaml
notification:
  retry:
    max-attempts: ${MAX_ATTEMPTS:3}
    initial-delay: ${INITIAL_RETRY_DELAY:PT1S}
    max-delay: ${MAX_RETRY_DELAY:PT1M}
```

El proceso que clasifica el error calcula y persiste `nextRetryAt`. El scheduler no recalcula el backoff.

El jitter evita que muchas notificaciones vuelvan a golpear simultáneamente un webhook degradado. Los delays deben ser configurables para que los tests no dependan de esperas reales.

## 12. Scheduler de retries

Consulta eventos elegibles mediante:

```sql
delivery_status = 'RETRY_SCHEDULED'
AND next_retry_at <= UTC_TIMESTAMP(6)
```

Debe usar el índice `(delivery_status, next_retry_at)` y procesar en lotes acotados.

La transición `RETRY_SCHEDULED → PENDING` debe ser atómica. En esta iteración, un dispatcher local puede activar el procesamiento. El caso de uso dependerá de `NotificationQueue`, de modo que la última iteración pueda publicar el `eventId` en SQS sin cambiar el dominio.

## 13. Recovery

Debe recuperar registros abandonados en `PENDING` o `DELIVERING` cuando `updatedAt` supere el timeout configurado.

```text
DELIVERING
    ↓ proceso muere
updatedAt excede recovery timeout
    ↓ transición atómica
PENDING
```

La recuperación debe:

- operar en lotes;
- usar el índice `(delivery_status, updated_at)`;
- aplicar compare-and-set o condición equivalente;
- evitar que múltiples instancias recuperen el mismo registro;
- emitir métricas diferenciadas para recuperación de `PENDING` y `DELIVERING`.

Recovery no convierte el sistema en exactly-once: si el webhook procesó la solicitud pero la respuesta se perdió, el nuevo intento puede duplicar la entrega.

## 14. Semántica at-least-once

El servicio ofrece entrega **at-least-once**. No promete exactly-once.

```text
POST al webhook
    ↓
el cliente procesa
    ↓
la respuesta se pierde
    ↓
retry
    ↓
posible duplicado
```

La mitigación es un `event_id` estable y un consumidor idempotente. El contrato y la documentación deben comunicar esta responsabilidad al receptor.

## 15. Replay

```http
POST /notification_events/{eventId}/replay
```

Solo se permite desde `FAILED`. La transición debe ser atómica:

```text
FAILED → PENDING
```

Al aceptar el replay:

- `attemptCount = 0`;
- `nextRetryAt = null`;
- `lastError` se conserva para diagnóstico;
- `updatedAt = now`;
- se activa el mecanismo de procesamiento configurado.

Respuestas:

- `202 Accepted`, sin body: replay aceptado;
- `404 Not Found`: evento inexistente;
- `422 Unprocessable Entity`: evento existente en un estado diferente de `FAILED`.

Dos replays concurrentes no pueden generar dos activaciones válidas.

## 16. GET colección

```http
GET /notification_events?from=...&to=...&deliveryStatus=completed&page=1&pageSize=20
```

Reglas:

- filtros `from`, `to` y `deliveryStatus` combinables;
- rango máximo de un mes;
- `from` y `to` se interpretan en UTC;
- estados públicos permitidos: `completed` y `failed`;
- `page >= 1`;
- `1 <= pageSize <= 20`;
- orden estable: `event_created_at DESC, event_id DESC`;
- no se devuelve `total`.

Respuesta:

```json
{
  "events": [
    {
      "event_id": "EVT003",
      "event_type": "credit_transfer",
      "content": "...",
      "delivery_date": "2026-08-25T12:00:00Z",
      "delivery_status": "failed",
      "client_id": "CLIENT002"
    }
  ],
  "page": 1,
  "pageSize": 20
}
```

Inputs inválidos, estado no permitido, fechas invertidas o rango superior a un mes producen `400 Bad Request`.

## 17. GET detalle

```http
GET /notification_events/{eventId}
```

Respuesta:

```json
{
  "event_id": "EVT003",
  "event_type": "credit_transfer",
  "content": "...",
  "delivery_date": "2026-08-25T12:00:00Z",
  "delivery_status": "failed",
  "client_id": "CLIENT002"
}
```

No se exponen `attemptCount`, `lastError`, `nextRetryAt`, `createdAt` ni `updatedAt`. Un evento inexistente produce `404 Not Found`.

## 18. Persistencia MySQL

Tablas conceptuales: `notification_event` y `subscription`.

Decisiones:

- `event_id` es la PK y evita duplicados persistidos;
- `delivery_status` es un `ENUM` cerrado;
- `content` es texto, no JSON;
- timestamps en UTC con precisión de microsegundos;
- longitudes de IDs y contenido deben validarse contra el contrato final; los valores del fixture no constituyen máximos contractuales.

DDL de referencia:

```sql
CREATE TABLE notification_event (
    event_id VARCHAR(64) NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    content VARCHAR(1024) NOT NULL,
    event_created_at DATETIME(6) NOT NULL,
    delivery_date DATETIME(6) NULL,
    delivery_status ENUM(
        'PENDING',
        'DELIVERING',
        'RETRY_SCHEDULED',
        'COMPLETED',
        'FAILED',
        'NOT_SUBSCRIBED',
        'SUBSCRIPTION_INACTIVE'
    ) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id),
    INDEX idx_event_query
        (client_id, delivery_status, event_created_at, event_id),
    INDEX idx_retry_scheduler
        (delivery_status, next_retry_at),
    INDEX idx_recovery
        (delivery_status, updated_at)
);

CREATE TABLE subscription (
    subscription_id VARCHAR(64) NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (subscription_id),
    UNIQUE KEY uk_subscription_client (client_id)
);
```

No se crea FK entre `notification_event.client_id` y `subscription.client_id` para permitir el estado `NOT_SUBSCRIBED`.

## 19. Observabilidad

Métricas mínimas:

- delivery success rate;
- delivery failure rate;
- retry rate;
- latencia end-to-end;
- latencia del webhook;
- distribución de status HTTP;
- eventos en `RETRY_SCHEDULED`;
- eventos en `FAILED`;
- cantidad y antigüedad del backlog;
- recovery count;
- tamaño de DLQ cuando SQS sea incorporado.

Logs estructurados mínimos:

```text
traceId
eventId
clientId
deliveryStatus
attemptCount
httpStatus
durationMs
```

No se debe loguear el `content` completo ni secretos. El tracing debe cubrir inbound adapter, caso de uso, persistencia y llamada al webhook. Las métricas deben evitar etiquetas de alta cardinalidad como `eventId`.

## 20. Seguridad OWASP

### 20.1 Broken Access Control

Riesgo: un cliente consulta o reprocesa eventos de otro cliente.

Mitigaciones:

- autorización por tenant/cliente;
- no confiar en un `client_id` arbitrario del request;
- aplicar aislamiento en todas las queries;
- auditar replays.

La autenticación/autorización completa queda fuera del MVP, pero los ports y casos de uso no deben impedir incorporarla.

### 20.2 SSRF

Riesgo: una URL de webhook permite acceder a servicios internos o metadata de infraestructura.

Mitigaciones:

- aceptar solo HTTPS;
- validar esquema, host y puerto;
- bloquear loopback, private, link-local y metadata endpoints, también después de resolver DNS;
- desactivar redirects;
- aplicar restricciones de egress;
- mantener la URL fuera de inputs del evento.

### 20.3 Injection

Mitigaciones:

- consultas parametrizadas;
- validación de inputs y límites de longitud;
- nunca concatenar SQL;
- sanitizar datos antes de logs;
- no propagar cuerpos HTTP remotos sin límites.

Los secretos productivos deben almacenarse en AWS Secrets Manager o Parameter Store y entregarse mediante IAM de mínimo privilegio.

## 21. Tests obligatorios

### 21.1 Dominio y aplicación

- `2xx → COMPLETED`;
- suscripción inexistente → `NOT_SUBSCRIBED`;
- suscripción inactiva → `SUBSCRIPTION_INACTIVE`;
- `500/503 → RETRY_SCHEDULED`;
- `429` respeta `Retry-After`;
- `4xx` definitivo → `FAILED`;
- timeout o conexión incierta → retry;
- intentos agotados → `FAILED`;
- cálculo de exponential backoff, jitter y límite máximo.

### 21.2 Concurrencia y resiliencia

- dos procesos reclaman el mismo evento: solo uno obtiene el claim;
- el test verifica `webhook invocations == 1` para claims concurrentes;
- recovery de `PENDING` abandonado;
- recovery de `DELIVERING` abandonado;
- dos schedulers no reactivan dos veces el mismo retry;
- dos replays concurrentes generan una sola transición válida;
- un fallo después de procesar el webhook demuestra semántica at-least-once.

### 21.3 API

- replay de `FAILED → PENDING`, con `attemptCount = 0`;
- replay de estado diferente a `FAILED → 422`;
- recurso inexistente → `404`;
- colección paginada y orden estable;
- filtros de fecha y estado combinables;
- estado inválido → `400`;
- rango mayor a un mes → `400`;
- detalle no expone campos internos.

### 21.4 Persistencia e integración

- `event_id` duplicado no crea otra fila;
- transiciones usan condiciones sobre el estado anterior;
- índices soportan queries de API, scheduler y recovery;
- adapter HTTP clasifica status y timeouts correctamente;
- timestamps se persisten y serializan en UTC.

## 22. Configuración

Variables externas mínimas:

```text
WEBHOOK_URL
MAX_ATTEMPTS
INITIAL_RETRY_DELAY
MAX_RETRY_DELAY
PENDING_RECOVERY_TIMEOUT
WEBHOOK_CONNECT_TIMEOUT
WEBHOOK_READ_TIMEOUT
MAX_CONCURRENT_WEBHOOK_REQUESTS
RETRY_SCHEDULER_BATCH_SIZE
RECOVERY_BATCH_SIZE
```

No se deben hardcodear valores operacionales críticos dentro del dominio. La aplicación debe fallar al iniciar si una configuración obligatoria es inválida.

## 23. Criterios de aceptación

La iteración queda terminada cuando:

- los eventos pueden persistirse con `event_id` único;
- el fixture se carga con la adaptación de `event_created_at` documentada;
- se validan suscripciones inexistentes, activas e inactivas;
- el claim `PENDING → DELIVERING` es atómico;
- el webhook HTTPS recibe únicamente el payload público;
- respuestas HTTP y errores de red se clasifican según esta especificación;
- retries aplican backoff, jitter y `Retry-After`;
- scheduler y recovery operan con transiciones atómicas y lotes acotados;
- el sistema declara y demuestra semántica at-least-once;
- replay solo acepta eventos `FAILED` y responde `202`;
- GET colección soporta filtros, validación, orden y paginación definidos;
- GET detalle devuelve el contrato público y no filtra campos internos;
- esquema e índices MySQL soportan los flujos críticos;
- métricas, logs y tracing permiten diagnosticar éxito, fallos, retries y recovery;
- se documentan las mitigaciones de Broken Access Control, SSRF e Injection;
- los tests obligatorios pasan, incluido el test de concurrencia del webhook;
- no existe consumidor SQS en esta iteración;
- el dominio no depende del mecanismo temporal de ingreso ni de SQS.

## 24. Última iteración: incorporación de SQS Standard

La última iteración agregará exclusivamente la integración de entrada y transporte asincrónico con SQS Standard alrededor del flujo ya construido:

```text
Productor externo
      ↓
SQS Standard
      ↓
Inbound consumer adapter
      ↓
Persistencia idempotente por event_id
      ↓
Casos de uso existentes
```

También incorporará:

- DTO y validación del mensaje completo;
- consumer adapter desacoplado del dominio;
- `NotificationQueue` implementado con SQS para retries y replay cuando corresponda;
- delete/ACK del mensaje solo después de persistir el resultado del procesamiento;
- visibility timeout alineado con el tiempo máximo de procesamiento;
- DLQ y redrive policy para fallos repetidos de procesamiento;
- métricas de backlog, antigüedad del mensaje y DLQ;
- manejo idempotente de duplicados y entregas fuera de orden propias de SQS Standard;
- tests de duplicados, redelivery, fallos antes/después del ACK y DLQ.

SQS Standard no garantiza orden ni entrega única. El diseño continuará usando `event_id`, atomic claims y estados persistidos como controles de idempotencia y concurrencia. Si aparece un requisito real de orden por cliente, deberá evaluarse SQS FIFO como una decisión arquitectónica separada.

La incorporación de SQS no debe modificar el modelo de dominio, la máquina de estados, la política de retry, los contratos REST ni el cliente de webhook; solo debe agregar o reemplazar adapters.
