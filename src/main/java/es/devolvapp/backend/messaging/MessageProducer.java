package es.devolvapp.backend.messaging;

import es.devolvapp.backend.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class MessageProducer {
    private static final Logger log = LoggerFactory.getLogger(MessageProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public MessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public DevolvMessage send(String email, String type,String content) {
        DevolvMessage message = new DevolvMessage(email, type, content, Instant.now());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
        log.info("Mensaje enviado: {}", message);
        return message;
    }
}
