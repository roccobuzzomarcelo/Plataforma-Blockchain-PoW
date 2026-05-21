package com.blockchain.coordinator.service;

import com.blockchain.coordinator.messaging.TaskPublisher;
import com.blockchain.shared.event.BlockMinedEvent;
import com.blockchain.shared.event.MiningResultEvent;
import com.blockchain.shared.model.Block;
import com.blockchain.shared.model.MiningTask;
import com.blockchain.shared.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ConsensusService {

    private static final Logger log = LoggerFactory.getLogger(ConsensusService.class);

    private final BlockService blockService;
    private final TaskPublisher taskPublisher;
    private final RestTemplate restTemplate;

    @Value("${services.blockchain-api.url:http://localhost:8080}")
    private String blockchainApiUrl;

    private final ConcurrentHashMap<String, AtomicBoolean> taskResolved = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MiningTask> activeTasks = new ConcurrentHashMap<>();

    public ConsensusService(BlockService blockService,
            TaskPublisher taskPublisher,
            RestTemplate restTemplate) {
        this.blockService = blockService;
        this.taskPublisher = taskPublisher;
        this.restTemplate = restTemplate;
    }

    public void registerTask(MiningTask task) {
        activeTasks.put(task.taskId(), task);
        taskResolved.put(task.taskId(), new AtomicBoolean(false));
        log.debug("Tarea registrada: {}", task.taskId());
    }

    public void processResult(MiningResultEvent result) {
        if (!result.success()) {
            log.debug("Worker {} no encontró nonce en su rango para taskId={}",
                    result.workerId(), result.taskId());
            return;
        }

        AtomicBoolean resolved = taskResolved.get(result.taskId());
        if (resolved == null) {
            log.warn("TaskId desconocido: {}", result.taskId());
            return;
        }

        if (!resolved.compareAndSet(false, true)) {
            log.debug("Resultado tardío descartado de worker {} para tarea {}",
                    result.workerId(), result.taskId());
            return;
        }

        MiningTask task = activeTasks.get(result.taskId());
        if (task == null) {
            log.error("Tarea no encontrada en memoria para taskId={}", result.taskId());
            return;
        }

        // Verificar hash (NCT.3)
        String recomputedHash = HashUtils.powHash(result.nonce(), result.str(), result.bcContent());
        if (!recomputedHash.equals(result.blockHash())) {
            log.warn("Hash inválido del worker {}", result.workerId());
            resolved.set(false);
            return;
        }
        if (!recomputedHash.startsWith(task.prefix())) {
            log.warn("Hash no cumple el prefijo '{}'", task.prefix());
            resolved.set(false);
            return;
        }

        // Confirmar bloque (NCT.4)
        Instant now = Instant.now();
        Block confirmed = blockService.confirmBlock(
                task, result.nonce(), result.blockHash(), result.workerId(), now);

        activeTasks.remove(result.taskId());

        // Notificar a blockchain-api para broadcast WebSocket
        BlockMinedEvent event = new BlockMinedEvent(
                confirmed.index(),
                confirmed.blockHash(),
                result.workerId(),
                result.nonce(),
                task.prefix(),
                50.0);

        try {
            restTemplate.postForEntity(
                    blockchainApiUrl + "/api/events/block-mined",
                    event,
                    Void.class);
        } catch (Exception e) {
            log.warn("No se pudo notificar a blockchain-api: {}", e.getMessage());
        }

        log.info("BLOQUE {} CONFIRMADO. Worker ganador: {}, tiempo: {}ms",
                confirmed.index(), result.workerId(), result.elapsedMs());
    }
}