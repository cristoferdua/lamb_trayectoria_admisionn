# S13 - Validación end-to-end del producto del curso

*Guía única: agregar un microservicio nuevo al proyecto Lamb - Parte 13 de 16 - 2026*

## 1. Introducción

Levantas **todo** desde cero y pruebas tu servicio de punta a punta, como lo hará el profe en la evaluación.

## 2. Orden de arranque

| N. | Qué | Comando (cada uno en su terminal, desde la raíz) |
|---|---|---|
| 1 | Kafka y Keycloak de Lamb | `docker compose -f kafka/compose-dev.yml up -d` y `docker compose -f keycloak/compose-dev.yml up -d` |
| 2 | BD de tu servicio | `docker compose -f services/lamb-biblioteca-ms/compose-dev.yml up -d` |
| 3 | Observabilidad | `docker compose -f obs/compose-dev.yml up -d` |
| 4 | Config Server | `cd infra\lamb-config; .\mvnw.cmd spring-boot:run` |
| 5 | Eureka | `cd infra\lamb-eureka; .\mvnw.cmd spring-boot:run` |
| 6 | Tu servicio | `cd services\lamb-biblioteca-ms; .\mvnw.cmd spring-boot:run` |
| 7 | Gateway (al final) | `cd infra\lamb-gateway; .\mvnw.cmd spring-boot:run` |
| 8 | Frontend | `cd clients\lamb-frontend; npm start` |

Cambia solo `lamb-biblioteca-ms` por tu servicio. Admisión y Trayectoria no son necesarios para probar el tuyo.

## 3. Pruebas de la API

| N. | Prueba | Comando o link | Esperado |
|---|---|---|---|
| 1 | Hola mundo por Gateway | http://localhost:18080/api/v1/biblioteca/saludo | `200` + texto |
| 2 | Evento sin token | `Invoke-RestMethod -Method Post http://localhost:18080/api/v1/biblioteca/saludo/evento` | `401` |
| 3 | Evento directo | `Invoke-RestMethod -Method Post http://localhost:8095/api/v1/biblioteca/saludo/evento` | `200` + evento en el log |

**Camino B - Con CRUD:**

| N. | Prueba | Esperado |
|---|---|---|
| 4 | `GET http://localhost:18080/api/v1/libros` | `200` |
| 5 | `POST` sin token por el Gateway | `401` |
| 6 | `POST http://localhost:8095/api/v1/libros` con datos válidos | `201` + `libro.creado` |
| 7 | `POST` con `{"titulo":""}` | `400` "Error de validación en los datos enviados" y **sin** evento |
| 8 | `PUT .../libros/{id}` | `200` + `libro.actualizado` |
| 9 | `GET .../libros/99999` | `404` "Libro no encontrado: 99999" |
| 10 | `DELETE .../libros/{id}` | `204` + `libro.eliminado` |
| 11 | Datos con tildes y ñ (`"Año"`) | se guardan bien |

!!! note "Ver el código de error en PowerShell"
    `Invoke-RestMethod` lanza una excepción con `400`/`404`. Para ver solo el código:
    ```powershell
    try { Invoke-RestMethod http://localhost:8095/api/v1/libros/99999 } catch { $_.Exception.Response.StatusCode.value__ }
    ```

## 4. Pruebas en el navegador

| Usuario | Prueba | Esperado |
|---|---|---|
| `admin` / `admin123` | http://localhost:4200/biblioteca → *Enviar evento* | "Evento enviado a Kafka." |
| `admin` | **Camino B:** nuevo, editar, eliminar | todo funciona |
| `estudiante1` / `estudiante123` | *Enviar evento* (y en B: *Guardar*) | "No tienes permiso para esta acción." |

## 5. Revisión de infraestructura

| Qué | Link |
|---|---|
| Config | http://localhost:18888/lamb-biblioteca-ms/dev |
| Eureka | http://localhost:18761 |
| Swagger | http://localhost:8095/swagger-ui.html |
| Kafka UI | http://localhost:18090 |
| Keycloak | http://localhost:18180 |
| Prometheus | http://localhost:19090/targets |
| Grafana | http://localhost:13000 |

## 6. Evidencia de cierre de la S13

Una tabla como las de arriba con una columna **Resultado** (OK / falla) llena por ti, con capturas.
Si algo falla, ve a la [S14](S14_Revision_Tecnica_Estabilizacion.md).
