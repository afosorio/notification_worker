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

La API queda disponible en `http://localhost:8080`. La conexión a MySQL y la URL del webhook se pueden sobrescribir mediante `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `NOTIFICATION_WEBHOOK_URL`.
