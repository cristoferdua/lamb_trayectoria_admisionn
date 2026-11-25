package pe.edu.upeu.lamb.hola.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.edu.upeu.lamb.hola.event.HolaEvento;

@Slf4j
@Component
public class HolaEventosConsumer {

    @KafkaListener(topics = "${app.kafka.topic.hola}")
    public void alRecibirEvento(HolaEvento evento) {
        log.info("component=consumer eventType={} id={} mensaje=\"{}\" status=consumed",
                evento.getTipoEvento(), evento.getId(), evento.getMensaje());
    }
}
