# Notification Worker

Aplicación Spring Boot para el MVP de notificaciones.

## Requisitos

- Java 21 o superior
- Maven 3.9+ (o Maven Wrapper cuando se incorpore al repositorio)
- Docker Compose

## Arranque local

1. Levantar MySQL:

   ```bash
   docker compose up -d mysql
   ```

2. Iniciar la aplicación:

   ```bash
   mvn spring-boot:run
   ```

La API queda disponible en `http://localhost:8080`. La conexión a MySQL y la configuración operativa se pueden sobrescribir mediante:

```text
DB_URL, DB_USERNAME, DB_PASSWORD
WEBHOOK_URL
WEBHOOK_CONNECT_TIMEOUT, WEBHOOK_READ_TIMEOUT, MAX_CONCURRENT_WEBHOOK_REQUESTS
MAX_ATTEMPTS, INITIAL_RETRY_DELAY, MAX_RETRY_DELAY
PENDING_RECOVERY_TIMEOUT, RETRY_SCHEDULER_BATCH_SIZE, RECOVERY_BATCH_SIZE
```

El estado de la aplicación está disponible en `GET /actuator/health` y las métricas en `/actuator/metrics`.

## API de demo

```bash
curl 'http://localhost:8080/notification_events?clientId=CLIENT002&deliveryStatus=FAILED'
curl 'http://localhost:8080/notification_events/EVT003'
curl -i -X POST 'http://localhost:8080/notification_events/EVT003/replay'
```

El replay responde `202` y deja el evento en `PENDING`. Un segundo replay responde `422`. Un ID inexistente responde `404`.

## Arquitectura

```text
REST Controller / Local Queue Listener
             |
             v
       application services
       /       |         \
repository  subscription  webhook / queue ports
   |            |             |
  MySQL        MySQL       HTTPS / local queue
```

El dominio y la capa de aplicación dependen de puertos; no conocen Spring MVC, JPA, MySQL ni el cliente HTTP.

## Alcance y evolución

Este MVP incluye Flyway, persistencia MySQL, consulta paginada, replay condicional, cola local y webhook HTTPS. La entrega desde la cola usa semántica at-least-once: puede existir un duplicado si el proceso cae después de confirmar la base de datos y antes de publicar/confirmar la cola.

Quedan diferidos para producción el adaptador SQS/DLQ, scheduler de reintentos, recuperación de trabajos abandonados, circuit breaker, rate limiting, métricas, trazas distribuidas y autenticación real. En producción `clientId` debe derivarse de la identidad autenticada, no de un parámetro explícito.
