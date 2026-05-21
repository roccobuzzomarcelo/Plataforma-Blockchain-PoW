package com.blockchain.worker.messaging;

import com.blockchain.shared.event.MiningResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import static com.blockchain.worker.config.RabbitMQConfig.RESULTS_EXCHANGE;
import static com.blockchain.worker.config.RabbitMQConfig.RESULTS_QUEUE;

/**
 * Publica el resultado del minado al coordinator via RabbitMQ.
 */
@Component
public class ResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(ResultPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ResultPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(MiningResultEvent result) {
        rabbitTemplate.convertAndSend(RESULTS_EXCHANGE, RESULTS_QUEUE, result);
        log.debug("Resultado publicado: taskId={}, success={}", result.taskId(), result.success());
    }
}