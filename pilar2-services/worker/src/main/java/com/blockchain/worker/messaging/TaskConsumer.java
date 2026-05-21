package com.blockchain.worker.messaging;

import com.blockchain.shared.event.MiningResultEvent;
import com.blockchain.shared.model.MiningTask;
import com.blockchain.worker.miner.PoWMiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Recibe tareas de minería del exchange fanout.
 * Por cada tarea, lanza el PoWMiner y publica el resultado.
 */
@Component
public class TaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskConsumer.class);

    private final ResultPublisher resultPublisher;
    private final int threadCount;
    private final String workerId;

    // Tarea actualmente en proceso (para descartar tareas duplicadas)
    private final AtomicReference<String> currentTaskId = new AtomicReference<>(null);

    public TaskConsumer(ResultPublisher resultPublisher,
            @Value("${worker.threads:8}") int threadCount,
            @Value("${worker.id:worker-1}") String workerId) {
        this.resultPublisher = resultPublisher;
        this.threadCount = threadCount;
        this.workerId = workerId;
    }

    @RabbitListener(queues = "#{miningQueue.name}")
    public void onTask(MiningTask task) {
        log.info("Tarea recibida: block={}, prefix={}, range=[{},{}]",
                task.blockIndex(), task.prefix(), task.rangeMin(), task.rangeMax());

        // Si ya estamos procesando esta tarea, ignorar
        if (!currentTaskId.compareAndSet(null, task.taskId())) {
            log.warn("Tarea {} ignorada, ya procesando {}", task.taskId(), currentTaskId.get());
            return;
        }

        long startMs = System.currentTimeMillis();
        PoWMiner miner = new PoWMiner(threadCount);

        try {
            Optional<PoWMiner.MinerResult> result = miner.mine(
                    task.str(), task.bcContent(),
                    task.prefix(), task.rangeMin(), task.rangeMax());

            long elapsedMs = System.currentTimeMillis() - startMs;

            if (result.isPresent()) {
                log.info("¡Nonce encontrado! nonce={}, hash={}, tiempo={}ms",
                        result.get().nonce(), result.get().hash(), elapsedMs);

                resultPublisher.publish(new MiningResultEvent(
                        task.taskId(),
                        workerId,
                        task.blockIndex(),
                        result.get().nonce(),
                        result.get().hash(),
                        task.str(),
                        task.bcContent(),
                        true,
                        elapsedMs));
            } else {
                log.info("No se encontró nonce en el rango asignado. Tiempo: {}ms", elapsedMs);

                resultPublisher.publish(new MiningResultEvent(
                        task.taskId(),
                        workerId,
                        task.blockIndex(),
                        -1L,
                        "",
                        task.str(),
                        task.bcContent(),
                        false,
                        elapsedMs));
            }
        } finally {
            currentTaskId.set(null); // liberar para la próxima tarea
        }
    }
}