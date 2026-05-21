package com.blockchain.coordinator.messaging;

import com.blockchain.coordinator.service.ConsensusService;
import com.blockchain.shared.event.MiningResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.blockchain.coordinator.config.RabbitMQConfig.RESULTS_QUEUE;

/**
 * Escucha la queue de resultados donde los workers publican sus soluciones.
 */
@Component
public class ResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResultConsumer.class);
    private final ConsensusService consensusService;

    public ResultConsumer(ConsensusService consensusService) {
        this.consensusService = consensusService;
    }

    @RabbitListener(queues = RESULTS_QUEUE)
    public void onResult(MiningResultEvent result) {
        log.debug("Resultado recibido de worker {}: success={}, nonce={}",
                result.workerId(), result.success(), result.nonce());
        consensusService.processResult(result);
    }
}