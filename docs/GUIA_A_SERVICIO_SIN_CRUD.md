# G1 - Microservicio sin CRUD (Hola mundo)

*Guía de práctica - 2026 - siguiendo el código de las sesiones de Pagatu*

## 1. Introducción

### 1.1 Qué vas a construir

Un microservicio nuevo dentro del proyecto Lamb (ejemplo: **`lamb-biblioteca-ms`**) escrito **igual que los
microservicios de Pagatu** (las guías del profe): mismas carpetas, mismas clases y mismo estilo de código.
Queda conectado a todo, como Admisión y Trayectoria:

| Conexión | Qué hace | Como en Pagatu |
|---|---|---|
| **Config Server** | Le da la configuración (puerto, BD, Kafka...) | [S2](https://262dist.github.io/pagatu/sesiones/S02_Configuracion_Centralizada_Ambientes/) |
| **Eureka** | Lo registra para que el Gateway lo encuentre | [S3](https://262dist.github.io/pagatu/sesiones/S03_Registro_Descubrimiento_Ejecucion_Concurrente/) |
| **Gateway** | Publica sus rutas en `http://localhost:18080` | [S4](https://262dist.github.io/pagatu/sesiones/S04_Punto_Unico_Acceso_Distribucion_Trafico/) |
| **Keycloak** | Login en el frontend; el Gateway exige roles | [S7b](https://262dist.github.io/pagatu/sesiones/S07b_Seguridad_Distribuida_Keycloak/) |
| **PostgreSQL + Flyway** | Su propia base de datos | [S1](https://262dist.github.io/pagatu/sesiones/S01_Construccion_Servicio_Base/) |
| **Kafka** | Envía y recibe eventos | [S8](https://262dist.github.io/pagatu/sesiones/S08_Mensajeria_Asincrona_Entre_Servicios/) |
| **Prometheus + Loki + Grafana** | Métricas y logs (con `traceId`) | [S10](https://262dist.github.io/pagatu/sesiones/S10_Observabilidad_Diagnostico_Sistemas_Distribuidos/) |
| **Frontend Angular** | Página simple, **sin diseño**, que dice "Hola mundo" | [S11](https://262dist.github.io/pagatu/sesiones/S11_Integracion_Cliente_Frontend_Angular_Keycloak/) |

> **¿Necesitas además agregar, editar y eliminar registros?**
> Haz esta guía completa y luego sigue la [G2 - Microservicio con CRUD simple](GUIA_B_SERVICIO_CON_CRUD.md).

### 1.2 Elige tus nombres (y úsalos en TODA la guía)

Toda la guía usa el ejemplo **biblioteca**. Cuando el profe pida otro servicio, reemplaza así
(en VS Code: `Ctrl+Shift+H`, buscar y reemplazar respetando mayúsculas):

| En la guía dice | 👉 Lo cambias por (ej. el profe pide "Tesorería") | Dónde se usa |
|---|---|---|
| `biblioteca` | `tesoreria` | carpetas, paquete Java, rutas, BD, topic |
| `Biblioteca` | `Tesoreria` | nombres de clases Java |
| `8095` | un puerto libre | puerto del servicio |
| `15460` | otro puerto libre (15461, 15462...) | puerto de su PostgreSQL |

**Puertos ya ocupados en Lamb:** 8081–8089, 15451–15459, 18080, 18090, 18092, 18180, 18761, 18888.
Para revisar si un puerto está libre (si **no imprime nada**, está libre):

```powershell
Get-NetTCPConnection -LocalPort 8095 -State Listen -ErrorAction SilentlyContinue
```

### 1.3 Estructura final (igual que en Pagatu)

```text
services\lamb-biblioteca-ms\
├── pom.xml, mvnw, mvnw.cmd, .mvn\, compose-dev.yml
└── src\main\
    ├── java\pe\edu\upeu\lamb\biblioteca\
    │   ├── LambBibliotecaMsApplication.java
    │   ├── config\      SecurityConfig, OpenApiConfig, KafkaTopicsConfig
    │   ├── controller\  SaludoController
    │   ├── event\       BibliotecaEvento
    │   ├── filter\      CorrelationIdFilter
    │   └── messaging\   BibliotecaEventosProducer, BibliotecaEventosConsumer
    └── resources\
        ├── application.yml
        ├── logback-spring.xml
        └── db\migration\           (vacía en esta guía)
```

## 2. Crear el proyecto

Abre PowerShell **en la raíz del proyecto** (`Lamb-Modulo02-Admision-Trayectoria`) y pega.
Copia el `pom.xml` y el Maven Wrapper de Trayectoria, que ya traen las mismas dependencias que
usa el profe en Pagatu (web, JPA, Flyway, Kafka, OAuth2 Resource Server, Config, Eureka, Swagger, Actuator, Prometheus, Lombok):

```powershell
$nuevo = "services\lamb-biblioteca-ms"          # 👉 CAMBIA biblioteca
New-Item -ItemType Directory $nuevo | Out-Null
Copy-Item -Recurse services\lamb-trayectoria-ms\.mvn $nuevo
Copy-Item services\lamb-trayectoria-ms\mvnw, services\lamb-trayectoria-ms\mvnw.cmd, services\lamb-trayectoria-ms\pom.xml, services\lamb-trayectoria-ms\.gitignore $nuevo
(Get-Content "$nuevo\pom.xml") -replace 'lamb-trayectoria-ms','lamb-biblioteca-ms' -replace '<description>.*</description>','<description>Microservicio de biblioteca</description>' | Set-Content -Encoding utf8 "$nuevo\pom.xml"
$java = "$nuevo\src\main\java\pe\edu\upeu\lamb\biblioteca"   # 👉 CAMBIA biblioteca
"config","controller","event","filter","messaging" | ForEach-Object { New-Item -ItemType Directory -Force "$java\$_" | Out-Null }
New-Item -ItemType Directory -Force "$nuevo\src\main\resources\db\migration" | Out-Null
```

## 3. Código Java

Todos los archivos van dentro de `services\lamb-biblioteca-ms\src\main\java\pe\edu\upeu\lamb\biblioteca\`.

### 3.1 `LambBibliotecaMsApplication.java`

```java
package pe.edu.upeu.lamb.biblioteca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LambBibliotecaMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LambBibliotecaMsApplication.class, args);
    }
}
```

### 3.2 `config\SecurityConfig.java`

Igual que `pagatu-pago-ms`: el servicio valida el token de Keycloak si viene, y **las reglas por rol
las decide el Gateway** (paso 7.2).

```java
package pe.edu.upeu.lamb.biblioteca.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

### 3.3 `config\OpenApiConfig.java` (Swagger con botón *Authorize*)

Igual que `pagatu-orden-ms`.

```java
package pe.edu.upeu.lamb.biblioteca.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
```

### 3.4 `config\KafkaTopicsConfig.java` (crea el topic)

Igual que en S8: el nombre del topic sale de la configuración (`app.kafka.topic.biblioteca`).

```java
package pe.edu.upeu.lamb.biblioteca.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic bibliotecaEventos(@Value("${app.kafka.topic.biblioteca}") String nombre) {
        return TopicBuilder.name(nombre).partitions(3).replicas(1).build();
    }
}
```

### 3.5 `filter\CorrelationIdFilter.java` (traceId en los logs)

Copia exacta del filtro de Pagatu: cada petición lleva un `X-Trace-ID` que aparece en los logs y en Grafana.

```java
package pe.edu.upeu.lamb.biblioteca.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-ID";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        try {
            MDC.put(MDC_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

### 3.6 `event\BibliotecaEvento.java` (el mensaje de Kafka)

Mismo formato que `OrdenCreadaEvento` / `PagoValidadoEvento` de Pagatu (`tipoEvento`, `origen`, `timestamp`).

```java
package pe.edu.upeu.lamb.biblioteca.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BibliotecaEvento {

    private String tipoEvento;
    private Long id;
    private String mensaje;
    private String origen;
    private Long timestamp;
}
```

> **👉 Más datos en el evento**
> Agrega campos aquí si el profe lo pide (ej. `private String dni;`).

### 3.7 `messaging\BibliotecaEventosProducer.java` (ENVÍA a Kafka)

Igual que `PagoEventosProducer`: si hay una transacción abierta, publica **después del commit**
(así nunca se avisa algo que no se guardó).

```java
package pe.edu.upeu.lamb.biblioteca.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.edu.upeu.lamb.biblioteca.event.BibliotecaEvento;

@Slf4j
@Component
@RequiredArgsConstructor
public class BibliotecaEventosProducer {

    private final KafkaTemplate<String, BibliotecaEvento> kafkaTemplate;

    @Value("${app.kafka.topic.biblioteca}")
    private String topicBiblioteca;

    public void publicarTrasCommit(BibliotecaEvento evento) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(evento);
                }
            });
        } else {
            enviar(evento);
        }
    }

    private void enviar(BibliotecaEvento evento) {
        kafkaTemplate.send(topicBiblioteca, String.valueOf(evento.getId()), evento)
                .whenComplete((resultado, ex) -> {
                    if (ex != null) {
                        log.error("component=producer topic={} eventType={} id={} status=error error=\"{}\"",
                                topicBiblioteca, evento.getTipoEvento(), evento.getId(), ex.getMessage());
                        return;
                    }
                    log.info("component=producer topic={} partition={} offset={} eventType={} id={} status=published",
                            resultado.getRecordMetadata().topic(),
                            resultado.getRecordMetadata().partition(),
                            resultado.getRecordMetadata().offset(),
                            evento.getTipoEvento(), evento.getId());
                });
    }
}
```

### 3.8 `messaging\BibliotecaEventosConsumer.java` (RECIBE de Kafka)

```java
package pe.edu.upeu.lamb.biblioteca.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.edu.upeu.lamb.biblioteca.event.BibliotecaEvento;

@Slf4j
@Component
public class BibliotecaEventosConsumer {

    @KafkaListener(topics = "${app.kafka.topic.biblioteca}")
    public void alRecibirEvento(BibliotecaEvento evento) {
        log.info("component=consumer eventType={} id={} mensaje=\"{}\" status=consumed",
                evento.getTipoEvento(), evento.getId(), evento.getMensaje());
    }
}
```

> **👉 Reaccionar a un evento**
> Dentro de `alRecibirEvento(...)` va lo que debe pasar al llegar el mensaje. Para escuchar el topic
> de **otro** servicio, mira `OrdenEventosConsumer` de `pagatu-pago-ms` o `AdmisionEventosConsumer` de Trayectoria.

### 3.9 `controller\SaludoController.java` (Hola mundo)

Como el `SaludoController` de S1, más un endpoint que manda un evento a Kafka.

```java
package pe.edu.upeu.lamb.biblioteca.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.upeu.lamb.biblioteca.event.BibliotecaEvento;
import pe.edu.upeu.lamb.biblioteca.messaging.BibliotecaEventosProducer;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/v1/biblioteca")
@RequiredArgsConstructor
public class SaludoController {

    private final BibliotecaEventosProducer producer;

    @Value("${spring.application.name}")
    private String nombreServicio;

    @GetMapping("/saludo")
    public String saludo() {
        log.info("Saludo solicitado");
        return "Hola mundo desde " + nombreServicio;
    }

    @PostMapping("/saludo/evento")
    public BibliotecaEvento enviarEvento() {
        BibliotecaEvento evento = BibliotecaEvento.builder()
                .tipoEvento("saludo.enviado")
                .id(System.currentTimeMillis())
                .mensaje("Hola mundo por Kafka")
                .origen(nombreServicio)
                .timestamp(Instant.now().toEpochMilli())
                .build();
        producer.publicarTrasCommit(evento);
        return evento;
    }
}
```

## 4. Archivos de `src\main\resources\`

### 4.1 `application.yml`

Igual que en S2: solo el nombre y dónde está el Config Server; lo demás vive en el Config Server (paso 6).

```yaml
spring:
  application:
    name: lamb-biblioteca-ms
  profiles:
    active: dev
  config:
    import: "optional:configserver:${CONFIG_SERVER_URL:http://localhost:18888}"
```

### 4.2 `logback-spring.xml` (logs con traceId, para Loki)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <property name="LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{traceId}] %-5level %logger{36} - %msg%n"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/biblioteca.log</file>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/biblioteca-%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>7</maxHistory>
        </rollingPolicy>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

### 4.3 Carpeta de migraciones

Deja creada `src\main\resources\db\migration\` (vacía). En la G2 aquí va la tabla.

## 5. Base de datos: `services\lamb-biblioteca-ms\compose-dev.yml`

```yaml
name: lamb-biblioteca-dev

services:
  postgres-biblioteca-dev:
    image: postgres:16-alpine
    container_name: lamb-postgres-biblioteca-dev
    restart: unless-stopped
    environment:
      POSTGRES_DB: lamb_biblioteca_db
      POSTGRES_USER: lamb
      POSTGRES_PASSWORD: lamb
    ports:
      - "15460:5432"
    volumes:
      - lamb_biblioteca_dev_data:/var/lib/postgresql/data

volumes:
  lamb_biblioteca_dev_data:
    name: lamb-biblioteca-data
```

> **El `name:` del volumen**
> Dale un nombre propio. Si dos proyectos usan el mismo nombre de volumen, **comparten la base de datos**
> y Flyway falla con `Migration checksum mismatch`.

## 6. Config Server: `infra\lamb-config\config-repo\lamb-biblioteca-ms-dev.yml`

Mismo formato que `pagatu-pago-ms-dev.yml` del profe. 👉 Cambia el puerto, la BD, el paquete del evento y el topic.

```yaml
server:
  port: 8095

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:18180/realms/lamb

  datasource:
    url: jdbc:postgresql://localhost:15460/lamb_biblioteca_db
    username: lamb
    password: lamb
    driver-class-name: org.postgresql.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  devtools:
    restart:
      enabled: true
      quiet-period: 2s
      poll-interval: 3s
    livereload:
      enabled: true
  kafka:
    bootstrap-servers: localhost:18092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JacksonJsonSerializer
      properties:
        spring.json.add.type.headers: false
    consumer:
      group-id: lamb-biblioteca-ms
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JacksonJsonDeserializer
        spring.json.value.default.type: pe.edu.upeu.lamb.biblioteca.event.BibliotecaEvento
        spring.json.trusted.packages: pe.edu.upeu.lamb.biblioteca.event
        spring.json.use.type.headers: false

app:
  kafka:
    topic:
      biblioteca: biblioteca-eventos

springdoc:
  swagger-ui:
    path: /swagger-ui.html

logging:
  level:
    pe.edu.upeu.lamb.biblioteca: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState, db

eureka:
  instance:
    hostname: localhost
    instance-id: ${spring.application.name}:${server.port}
  client:
    service-url:
      defaultZone: http://localhost:18761/eureka
```

> **El nombre del archivo importa**
> Debe ser `spring.application.name` + `-dev.yml` (aquí `lamb-biblioteca-ms-dev.yml`), si no el servicio no toma la configuración.

## 7. Gateway

### 7.1 La ruta: `infra\lamb-config\config-repo\lamb-gateway-dev.yml`

Como en Pagatu, **una ruta por recurso**. Busca la línea `- id: lamb-finanzas` y **justo arriba** pega:

```yaml
            - id: lamb-biblioteca-saludo
              uri: lb://lamb-biblioteca-ms
              predicates:
                - Path=/api/v1/biblioteca/**
```

### 7.2 La seguridad (Keycloak): `infra\lamb-gateway\src\main\java\pe\edu\upeu\lamb\gateway\config\SecurityConfig.java`

Igual que el Gateway de Pagatu (`GET` libre, escribir solo `ADMIN`). Busca la línea
`.requestMatchers(TRAYECTORIA).permitAll()` y **debajo** pega:

```java
                        .requestMatchers(HttpMethod.GET, "/api/v1/biblioteca/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/biblioteca/**").hasRole("ADMIN")
```

👉 Cambia los roles según lo que pida el profe: `hasAnyRole("ADMIN", "COORDINADOR")`, o `.authenticated()`
para "cualquier usuario logueado". Roles del realm `lamb`: `ADMIN`, `COORDINADOR`, `DOCENTE`, `ESTUDIANTE`.

> **Reinicia el Gateway**
> Cambiaste código Java del Gateway: detenlo y vuelve a arrancarlo. En Windows, si lo corres con
> `java -jar`, detenlo **antes** de recompilar (el `.jar` queda bloqueado).

## 8. Grafana

- **Métricas:** no haces nada. Prometheus descubre el servicio solo, vía Eureka.
- **Logs:** en `obs\promtail\promtail-config-dev.yml`, busca `- job_name: lamb-gateway` y **justo arriba** pega:

```yaml
  - job_name: lamb-biblioteca-ms
    static_configs:
      - targets: [localhost]
        labels:
          application: lamb-biblioteca-ms
          __path__: /var/log/lamb/lamb-biblioteca-ms/logs/*.log
```

## 9. Frontend: página "Hola mundo" (sin diseño)

Como en S11 de Pagatu: un **service** que llama al Gateway y un **componente** con su `.ts` y su `.html`.
Crea la carpeta `clients\lamb-frontend\src\app\features\biblioteca\`.

### 9.1 `biblioteca-service.ts`

```ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class BibliotecaService {
  private readonly http = inject(HttpClient);

  saludo(): Observable<string> {
    return this.http.get(`${environment.apiUrl}/api/v1/biblioteca/saludo`, { responseType: 'text' });
  }

  enviarEvento(): Observable<unknown> {
    return this.http.post(`${environment.apiUrl}/api/v1/biblioteca/saludo/evento`, {});
  }
}
```

### 9.2 `saludo.ts`

```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { BibliotecaService } from './biblioteca-service';

@Component({
  selector: 'app-saludo',
  templateUrl: './saludo.html',
})
export class Saludo implements OnInit {
  private readonly bibliotecaService = inject(BibliotecaService);
  protected readonly mensaje = signal('Cargando...');
  protected readonly resultado = signal<string | null>(null);

  ngOnInit(): void {
    this.bibliotecaService.saludo().subscribe({
      next: (texto) => this.mensaje.set(texto),
      error: () => this.mensaje.set('No se pudo conectar con el servicio.'),
    });
  }

  enviarEvento(): void {
    this.bibliotecaService.enviarEvento().subscribe({
      next: () => this.resultado.set('Evento enviado a Kafka.'),
      error: (err: HttpErrorResponse) =>
        this.resultado.set(err.status === 403 ? 'No tienes permiso para esta acción.' : 'No se pudo enviar el evento.'),
    });
  }
}
```

### 9.3 `saludo.html`

```html
<h1>{{ mensaje() }}</h1>

<button (click)="enviarEvento()">Enviar evento a Kafka</button>
@if (resultado()) {
  <p>{{ resultado() }}</p>
}
```

### 9.4 La ruta: `clients\lamb-frontend\src\app\app.routes.ts`

Busca la última línea `{ path: '**', redirectTo: '' }` y **justo arriba** pega:

```ts
  // ====== BIBLIOTECA (paginas simples, sin diseño; piden login de Keycloak) ======
  {
    path: 'biblioteca',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/biblioteca/saludo').then((m) => m.Saludo)
  },
```

`adminGuard` pide login de Keycloak (cualquier usuario) y el token viaja solo al Gateway.

## 10. Ejecutar

```powershell
# Infraestructura (si está apagada)
docker compose -f kafka/compose-dev.yml up -d
docker compose -f keycloak/compose-dev.yml up -d
docker compose -f services/lamb-admision-ms/compose-dev.yml up -d
docker compose -f services/lamb-trayectoria-ms/compose-dev.yml up -d
docker compose -f services/lamb-biblioteca-ms/compose-dev.yml up -d     # 👈 NUEVO
docker compose -f obs/compose-dev.yml up -d
docker restart lamb-promtail-dev                                         # lee el job nuevo
```

Cada uno en su terminal, en este orden:

```powershell
cd infra\lamb-config;             .\mvnw.cmd spring-boot:run   # 1. Config
cd infra\lamb-eureka;             .\mvnw.cmd spring-boot:run   # 2. Eureka
cd services\lamb-admision-ms;     .\mvnw.cmd spring-boot:run   # 3. Admisión
cd services\lamb-trayectoria-ms;  .\mvnw.cmd spring-boot:run   # 4. Trayectoria
cd services\lamb-biblioteca-ms;   .\mvnw.cmd spring-boot:run   # 5. 👈 TU SERVICIO NUEVO
cd infra\lamb-gateway;            .\mvnw.cmd spring-boot:run   # 6. Gateway (al final)
cd clients\lamb-frontend;         npm start                    # 7. Frontend
```

> **Eureka tarda ~30 segundos**
> Si el Gateway responde `503`/`500 Unable to find instance`, espera medio minuto y vuelve a probar.

## 11. Comprobar

| Qué | Link | Qué debes ver |
|---|---|---|
| **Página** | http://localhost:4200/biblioteca | Login (`admin` / `admin123`) y "Hola mundo desde lamb-biblioteca-ms" |
| Por el Gateway | http://localhost:18080/api/v1/biblioteca/saludo | `Hola mundo desde lamb-biblioteca-ms` |
| Swagger | http://localhost:8095/swagger-ui.html | La API documentada |
| Salud | http://localhost:8095/actuator/health | `"status":"UP"` |
| Eureka | http://localhost:18761 | `LAMB-BIBLIOTECA-MS` |
| Kafka UI | http://localhost:18090 | Topic `biblioteca-eventos` |
| Prometheus | http://localhost:19090/targets | `lamb-biblioteca-ms` UP |
| Grafana | http://localhost:13000 | `admin` / `admin` → Explore → Loki → `{application="lamb-biblioteca-ms"}` |
| Config Server | http://localhost:18888/lamb-biblioteca-ms/dev | La config del paso 6 |

**Probar Kafka:** en la página, pulsa *Enviar evento a Kafka* (entra como `admin`). En la consola del servicio salen:

```text
... component=producer topic=biblioteca-eventos ... eventType=saludo.enviado ... status=published
... component=consumer eventType=saludo.enviado ... status=consumed
```

**Probar la seguridad del Gateway:**

| Prueba | Resultado |
|---|---|
| `GET /api/v1/biblioteca/saludo` sin login | `200` |
| `POST /api/v1/biblioteca/saludo/evento` sin token | `401` |
| Botón *Enviar evento* entrando como `estudiante1` / `estudiante123` | "No tienes permiso para esta acción." (`403`) |
| Botón *Enviar evento* entrando como `admin` / `admin123` | "Evento enviado a Kafka." |

## 12. Problemas comunes

| Síntoma | Causa | Solución |
|---|---|---|
| `Port 8095 was already in use` | Otro programa usa el puerto | Elige otro (1.2) y cámbialo en el paso 6 |
| Gateway `503` / `500 Unable to find instance` | Eureka aún no lo registra | Espera 30 s; revisa http://localhost:18761 |
| Gateway `404` | Falta la ruta del paso 7.1 | Agrégala y reinicia el Gateway |
| Gateway `401` / `403` | Regla del paso 7.2 | Entra con un usuario que tenga el rol |
| `Migration checksum mismatch` | Editaste una migración aplicada o la BD es compartida | Crea `V2__...sql`; revisa el `name:` del volumen |
| `Unable to rename ... .jar` | El servicio sigue corriendo | Detenlo y vuelve a compilar |
| No salen logs en Grafana | Promtail sin reiniciar o servicio arrancado desde otra carpeta | `docker restart lamb-promtail-dev`; arranca desde `services\lamb-biblioteca-ms` |
| El servicio no toma la config | Config Server apagado o archivo mal nombrado | Paso 6 |
