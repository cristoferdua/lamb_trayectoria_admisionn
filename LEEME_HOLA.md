# Lamb con servicio Hola mundo (lamb-hola-ms)

Copia del proyecto Lamb (Admisión + Trayectoria) con un microservicio nuevo **solo Hola mundo**
(camino A de la guía, sin CRUD), conectado a Config Server, Eureka, Gateway, Keycloak, Kafka y Grafana.

## Datos del servicio

| Qué | Valor |
|---|---|
| Servicio | `services/lamb-hola-ms` (paquete `pe.edu.upeu.lamb.hola`) |
| Puerto | `8097` |
| Base de datos | `localhost:15461` · BD `lamb_hola_db` · `lamb` / `lamb` (sin tablas) |
| Topic Kafka | `hola-eventos` |
| Frontend | `http://localhost:4200/hola` |

## Archivos que se agregaron o cambiaron

- `services/lamb-hola-ms/` (nuevo)
- `infra/lamb-config/config-repo/lamb-hola-ms-dev.yml` (nuevo)
- `infra/lamb-config/config-repo/lamb-gateway-dev.yml` (ruta `lamb-hola-saludo`)
- `infra/lamb-gateway/.../config/SecurityConfig.java` (GET libre, POST solo ADMIN)
- `obs/promtail/promtail-config-dev.yml` (job `lamb-hola-ms`)
- `clients/lamb-frontend/src/app/features/hola/` (nuevo) y `app.routes.ts` (ruta `hola`)

## Arrancar (cada uno en su terminal, desde esta carpeta)

```powershell
docker compose -f kafka/compose-dev.yml up -d
docker compose -f keycloak/compose-dev.yml up -d
docker compose -f services/lamb-hola-ms/compose-dev.yml up -d

cd infra\lamb-config;          .\mvnw.cmd spring-boot:run
cd infra\lamb-eureka;          .\mvnw.cmd spring-boot:run
cd services\lamb-hola-ms;      .\mvnw.cmd spring-boot:run
cd infra\lamb-gateway;         .\mvnw.cmd spring-boot:run
cd clients\lamb-frontend;      npm install; npm start
```

No lo levantes al mismo tiempo que el proyecto original: usan los mismos puertos (18888, 18761, 18080, 4200).

## Probar

| Qué | Link / usuario | Resultado |
|---|---|---|
| Por el Gateway | http://localhost:18080/api/v1/hola/saludo | `Hola mundo desde lamb-hola-ms` |
| Página | http://localhost:4200/hola con `admin` / `admin123` | Hola mundo + "Evento enviado a Kafka." |
| Sin permiso | `estudiante1` / `estudiante123` | "No tienes permiso para esta acción." |
| Swagger | http://localhost:8097/swagger-ui.html | |
| Eureka | http://localhost:18761 | `LAMB-HOLA-MS` |
| Kafka UI | http://localhost:18090 | topic `hola-eventos` |
