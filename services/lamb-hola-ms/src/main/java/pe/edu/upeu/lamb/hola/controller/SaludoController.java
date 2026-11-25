package pe.edu.upeu.lamb.hola.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.upeu.lamb.hola.event.HolaEvento;
import pe.edu.upeu.lamb.hola.messaging.HolaEventosProducer;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/v1/hola")
@RequiredArgsConstructor
public class SaludoController {

    private final HolaEventosProducer producer;

    @Value("${spring.application.name}")
    private String nombreServicio;

    @GetMapping("/saludo")
    public String saludo() {
        log.info("Saludo solicitado");
        return "Hola mundo desde " + nombreServicio;
    }

    @PostMapping("/saludo/evento")
    public HolaEvento enviarEvento() {
        HolaEvento evento = HolaEvento.builder()
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
