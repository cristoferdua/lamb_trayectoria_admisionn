package pe.edu.upeu.lamb.hola.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.edu.upeu.lamb.hola.event.HolaEvento;

@Slf4j
@Component
@RequiredArgsConstructor
public class HolaEventosProducer {

    private final KafkaTemplate<String, HolaEvento> kafkaTemplate;

    @Value("${app.kafka.topic.hola}")
    private String topicHola;

    public void publicarTrasCommit(HolaEvento evento) {
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

    private void enviar(HolaEvento evento) {
        kafkaTemplate.send(topicHola, String.valueOf(evento.getId()), evento)
                .whenComplete((resultado, ex) -> {
                    if (ex != null) {
                        log.error("component=producer topic={} eventType={} id={} status=error error=\"{}\"",
                                topicHola, evento.getTipoEvento(), evento.getId(), ex.getMessage());
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
