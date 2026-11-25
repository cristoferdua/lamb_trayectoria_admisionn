# S8 - Mensajería asíncrona entre servicios

*Guía única: agregar un microservicio nuevo al proyecto Lamb - Parte 8 de 16 - 2026*

!!! note "Antes de empezar: debe estar levantado"
    Desde la raíz del proyecto (`Lamb-Modulo02-Admision-Trayectoria`). Si ya está corriendo, no lo arranques otra vez.

    **Docker** (Docker Desktop abierto; se pueden pegar en la misma terminal):

    ```powershell
    docker compose -f kafka/compose-dev.yml up -d    # Kafka + Kafka UI
    docker compose -f services/lamb-biblioteca-ms/compose-dev.yml up -d    # BD de tu servicio
    ```

    **Servicios** (cada uno en **su propia terminal**, en este orden, esperando que diga `Started ...`):

    ```powershell
    cd infra\lamb-config; .\mvnw.cmd spring-boot:run    # Config Server
    cd infra\lamb-eureka; .\mvnw.cmd spring-boot:run    # Eureka
    ```

    Comprueba que responden:

    - Kafka + Kafka UI: http://localhost:18090
    - BD de tu servicio: `docker ps` → `lamb-postgres-biblioteca-dev` *Up*
    - Config Server: http://localhost:18888/lamb-biblioteca-ms/dev
    - Eureka: http://localhost:18761

    Tu servicio se arranca **en esta sesión**, después de agregar el código de Kafka.

## 1. Introducción

Tu servicio va a **publicar eventos** en Kafka (`localhost:18092`) y también **escucharlos**, igual que
`orden-ms` y `pago-ms` en Pagatu:

| Camino | Cuándo publica | Tipo de evento |
|---|---|---|
| A - Sin CRUD | al pulsar "Enviar evento" | `saludo.enviado` |
| B - Con CRUD | cada vez que se agrega, edita o elimina | `libro.creado`, `libro.actualizado`, `libro.eliminado` (y también `saludo.enviado`) |

La configuración de Kafka ya quedó lista en la S2 (`spring.kafka...` y `app.kafka.topic.biblioteca`).

Crea las carpetas `event` y `messaging`:

```powershell
$java = "services\lamb-biblioteca-ms\src\main\java\pe\edu\upeu\lamb\biblioteca"   # cambia por tu nombre
"event","messaging" | ForEach-Object { New-Item -ItemType Directory -Force "$java\$_" | Out-Null }
```

## 2. Código común (los dos caminos)

### 2.1 `config\KafkaTopicsConfig.java` (crea el topic al arrancar)

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

!!! note "Qué cambia"
    El nombre del método y la clave `app.kafka.topic.biblioteca` (debe ser la misma del `-dev.yml` de la S2).

### 2.2 `event\BibliotecaEvento.java` (el mensaje)

Mismo formato que `OrdenCreadaEvento` / `PagoValidadoEvento` de Pagatu.

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

!!! note "Qué cambia"
    El nombre de la clase (`TuServicioEvento`). Si te piden mandar más datos, agrega atributos
    (ej. `private String dni;`). Deja `tipoEvento`, `id`, `origen` y `timestamp`.

!!! note "Si cambias el nombre o el paquete de la clase"
    Actualiza `spring.json.value.default.type` y `spring.json.trusted.packages` en el `-dev.yml` (S2),
    o el consumidor no podrá leer los mensajes.

### 2.3 `messaging\BibliotecaEventosProducer.java` (ENVÍA) - copia tal cual

Igual que `PagoEventosProducer`. `publicarTrasCommit` se explica en la [S9](S09_Consistencia_Distribuida_Procesos_Negocio.md).

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

### 2.4 `messaging\BibliotecaEventosConsumer.java` (RECIBE)

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

!!! note "Qué cambia"
    Solo los nombres. Si te piden **hacer algo** al recibir el evento (guardar un historial, avisar...), va
    dentro de `alRecibirEvento(...)`. Para escuchar el topic de **otro** servicio (ej. `admision-eventos`), mira
    `AdmisionEventosConsumer` de Trayectoria o `OrdenEventosConsumer` de `pagatu-pago-ms`.

## 3. Camino A - Sin CRUD: el botón de evento

**Si elegiste el servicio sin CRUD (solo Hola mundo), reemplaza `controller\SaludoController.java` por esta versión**
(agrega `POST /api/v1/biblioteca/saludo/evento`):

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

## 4. Camino B - Con CRUD: cada cambio avisa a Kafka

**Si elegiste el servicio con CRUD:**

1. Reemplaza `controller\SaludoController.java` por la versión de la sección 3 (también va en el camino B).
2. Reemplaza `service\LibroService.java` por esta versión, que publica un evento en cada `crear`, `actualizar` y `eliminar`:

```java
package pe.edu.upeu.lamb.biblioteca.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.lamb.biblioteca.dto.LibroRequest;
import pe.edu.upeu.lamb.biblioteca.dto.LibroResponse;
import pe.edu.upeu.lamb.biblioteca.entity.Libro;
import pe.edu.upeu.lamb.biblioteca.event.BibliotecaEvento;
import pe.edu.upeu.lamb.biblioteca.exception.ResourceNotFoundException;
import pe.edu.upeu.lamb.biblioteca.mapper.LibroMapper;
import pe.edu.upeu.lamb.biblioteca.messaging.BibliotecaEventosProducer;
import pe.edu.upeu.lamb.biblioteca.repository.LibroRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LibroService {

    private final LibroRepository libroRepository;
    private final LibroMapper libroMapper;
    private final BibliotecaEventosProducer producer;

    @Value("${spring.application.name}")
    private String nombreServicio;

    public List<LibroResponse> listar() {
        return libroRepository.findAll().stream()
                .map(libroMapper::toResponse)
                .toList();
    }

    public LibroResponse obtener(Long id) {
        return libroMapper.toResponse(buscarOFallar(id));
    }

    @Transactional
    public LibroResponse crear(LibroRequest request) {
        Libro libro = libroRepository.save(libroMapper.toEntity(request));
        publicar("libro.creado", libro);
        return libroMapper.toResponse(libro);
    }

    @Transactional
    public LibroResponse actualizar(Long id, LibroRequest request) {
        Libro libro = buscarOFallar(id);
        libro.setTitulo(request.getTitulo());
        libro.setAutor(request.getAutor());
        libro = libroRepository.save(libro);
        publicar("libro.actualizado", libro);
        return libroMapper.toResponse(libro);
    }

    @Transactional
    public void eliminar(Long id) {
        Libro libro = buscarOFallar(id);
        libroRepository.delete(libro);
        publicar("libro.eliminado", libro);
    }

    private Libro buscarOFallar(Long id) {
        return libroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Libro no encontrado: " + id));
    }

    private void publicar(String tipoEvento, Libro libro) {
        producer.publicarTrasCommit(BibliotecaEvento.builder()
                .tipoEvento(tipoEvento)
                .id(libro.getId())
                .mensaje(libro.getTitulo())
                .origen(nombreServicio)
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }
}
```

!!! note "Qué cambia respecto a la S1"
    Se agregaron: `producer`, `nombreServicio`, el método `publicar(...)` y una llamada `publicar("libro.xxx", libro)`
    en cada método que modifica. Cambia `"libro.creado"` etc. por tu recurso (`"pago.creado"`) y en
    `.mensaje(libro.getTitulo())` usa un atributo que identifique tu registro.

## 5. Probar

Reinicia tu servicio. En el log debe salir el topic creado y, al probar:

```powershell
# Camino A y B (directo al servicio, sin token)
Invoke-RestMethod -Method Post http://localhost:8095/api/v1/biblioteca/saludo/evento
```

En la consola del servicio salen **dos** líneas (envió y recibió):

```text
... component=producer topic=biblioteca-eventos partition=1 offset=1 eventType=saludo.enviado ... status=published
... component=consumer eventType=saludo.enviado ... mensaje="Hola mundo por Kafka" status=consumed
```

**Camino B:** crea, edita y elimina un libro (S4, paso 5, pero contra `http://localhost:8095/api/v1/libros`)
y verás `libro.creado`, `libro.actualizado` y `libro.eliminado`.

Mira los mensajes en **Kafka UI**: http://localhost:18090 → *Topics* → `biblioteca-eventos` → *Messages*.

!!! note "¿Por qué directo al 8095?"
    Por el Gateway el `POST` exige rol `ADMIN` (S7). Con usuario `admin` lo probarás desde el frontend en la S11.

!!! note "Archivos de esta sesión"
    **Creas:**

    - `services\lamb-biblioteca-ms\src\main\java\pe\edu\upeu\lamb\biblioteca\config\KafkaTopicsConfig.java`
    - `services\lamb-biblioteca-ms\src\main\java\pe\edu\upeu\lamb\biblioteca\event\BibliotecaEvento.java`
    - `services\lamb-biblioteca-ms\src\main\java\pe\edu\upeu\lamb\biblioteca\messaging\BibliotecaEventosProducer.java`
    - `services\lamb-biblioteca-ms\src\main\java\pe\edu\upeu\lamb\biblioteca\messaging\BibliotecaEventosConsumer.java`

    **Reemplazas:**

    - `services\lamb-biblioteca-ms\src\main\java\pe\edu\upeu\lamb\biblioteca\controller\SaludoController.java` - los dos caminos
    - `services\lamb-biblioteca-ms\src\main\java\pe\edu\upeu\lamb\biblioteca\service\LibroService.java` - solo camino B

## 6. Evidencia de cierre de la S8

- Líneas `component=producer ... status=published` y `component=consumer ... status=consumed` en el log.
- Captura del topic `biblioteca-eventos` con sus mensajes en Kafka UI.

Siguiente: [S9 - Consistencia distribuida](S09_Consistencia_Distribuida_Procesos_Negocio.md).
