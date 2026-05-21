package com.blockchain.coordinator.service;

import com.blockchain.coordinator.messaging.TaskPublisher;
import com.blockchain.shared.event.BlockMinedEvent;
import com.blockchain.shared.event.MiningResultEvent;
import com.blockchain.shared.model.Block;
import com.blockchain.shared.model.MiningTask;
import com.blockchain.shared.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Algoritmo de consenso:
 * - Acepta el PRIMER resultado válido que llegue para un bloque.
 * - Descarta todos los demás (llegaron tarde).
 * - Verifica el hash recalculando localmente antes de confirmar.
 */
@Service
public class ConsensusService {

    private static final Logger log = LoggerFactory.getLogger(ConsensusService.class);

    private final BlockService blockService;
    private final TaskPublisher taskPublisher;
    private final SimpMessagingTemplate ws;

    // taskId → flag de si ya fue ganado
    private final ConcurrentHashMap<String, AtomicBoolean> taskResolved = new ConcurrentHashMap<>();

    // taskId → MiningTask activa (para poder verificar)
    private final ConcurrentHashMap<String, MiningTask> activeTasks = new ConcurrentHashMap<>();

    public ConsensusService(BlockService blockService,
            TaskPublisher taskPublisher,
            SimpMessagingTemplate ws) {
        this.blockService = blockService;
        this.taskPublisher = taskPublisher;
        this.ws = ws;
    }

    /** Registra una tarea como activa cuando es publicada. */
    public void registerTask(MiningTask task) {
        activeTasks.put(task.taskId(), task);
        taskResolved.put(task.taskId(), new AtomicBoolean(false));
        log.debug("Tarea registrada: {}", task.taskId());
    }

    /**
     * Procesa el resultado de un worker.
     * Thread-safe: usa CAS para que solo el primer ganador gane.
     */
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

        // CAS: solo el primer thread en poner true gana
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

        // Verificar el hash localmente (NCT.3)
        String recomputedHash = HashUtils.powHash(result.nonce(), result.str(), result.bcContent());
        if (!recomputedHash.equals(result.blockHash())) {
            log.warn("Hash inválido del worker {}. Esperado: {}, Recibido: {}",
                    result.workerId(), recomputedHash, result.blockHash());
            resolved.set(false); // permitir que otro worker intente
            return;
        }
        if (!recomputedHash.startsWith(task.prefix())) {
            log.warn("Hash no cumple el prefijo '{}': {}", task.prefix(), recomputedHash);
            resolved.set(false);
            return;
        }

        // Confirmar bloque (NCT.4)
        Instant now = Instant.now();
        Block confirmed = blockService.confirmBlock(task, result.nonce(),
                result.blockHash(), result.workerId(), now);

        // Limpiar estado interno
        activeTasks.remove(result.taskId());

        // Broadcast WebSocket a todos los clientes
        BlockMinedEvent event = new BlockMinedEvent(
                confirmed.index(),
                confirmed.blockHash(),
                result.workerId(),
                result.nonce(),
                task.prefix(),
                50.0);
        ws.convertAndSend("/topic/blocks", event);

        log.info("BLOQUE {} CONFIRMADO. Worker ganador: {}, tiempo: {}ms",
                confirmed.index(), result.workerId(), result.elapsedMs());
    }
}