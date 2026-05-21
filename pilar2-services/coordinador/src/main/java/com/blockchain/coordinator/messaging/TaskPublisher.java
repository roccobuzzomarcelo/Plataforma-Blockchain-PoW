package com.blockchain.coordinator.messaging;

import com.blockchain.coordinator.config.RabbitMQConfig;
import com.blockchain.shared.model.MiningTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica tareas de minería en el exchange fanout de RabbitMQ.
 * Todos los workers suscritos reciben la tarea simultáneamente.
 */
@Component
public class TaskPublisher {

    private static final Logger log = LoggerFactory.getLogger(TaskPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public TaskPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTask(MiningTask task) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.MINING_EXCHANGE, "", task);
        log.info("Tarea publicada → block={}, prefix={}, range=[{},{}]",
                task.blockIndex(), task.prefix(), task.rangeMin(), task.rangeMax());
    }
}