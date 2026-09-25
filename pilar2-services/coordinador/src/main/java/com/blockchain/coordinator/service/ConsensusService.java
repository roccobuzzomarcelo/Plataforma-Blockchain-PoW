package com.blockchain.coordinator.service;

import com.blockchain.coordinator.messaging.TaskPublisher;
import com.blockchain.shared.event.BlockMinedEvent;
import com.blockchain.shared.event.MiningResultEvent;
import com.blockchain.shared.model.Block;
import com.blockchain.shared.model.MiningTask;
import com.blockchain.shared.util.HashUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Registra tareas de minería y resuelve el consenso (NCT.2/NCT.3): el
 * primer resultado válido gana, los demás se descartan.
 *
 * Con 2+ réplicas del coordinator, todas escuchan la MISMA cola de
 * RabbitMQ ("mining.results") como consumidores en competencia: cada
 * resultado lo recibe UNA sola réplica, pero no necesariamente la misma
 * que registró la tarea originalmente (esa la registró quien atendió el
 * POST /mine-block). Guardar el estado en un ConcurrentHashMap en memoria
 * rompía esto: la réplica que recibía el resultado ganador no encontraba
 * la tarea y el bloque nunca se confirmaba. Por eso el estado de
 * consenso vive en Redis, visible para cualquier réplica.
 *
 * Además, desde que un bloque se divide en varios chunks de rango de
 * nonce (ver BlockService.buildMiningTasks), el CAS de "quién gana"
 * está indexado por blockIndex y no por taskId: dos chunks del mismo
 * bloque podrían resolver casi al mismo tiempo, y sin esto ambos
 * intentarían confirmar el mismo índice -BlockService.confirmBlock no
 * es idempotente, un segundo confirm pisaría al primero en silencio.
 */
@Service
public class ConsensusService {

    private static final Logger log = LoggerFactory.getLogger(ConsensusService.class);
    private static final String TASK_KEY_PREFIX = "task:active:";
    private static final String RESOLVED_KEY_PREFIX = "task:resolved:";
    private static final Duration TASK_TTL = Duration.ofHours(1);

    private final BlockService blockService;
    @SuppressWarnings("unused")
    private final TaskPublisher taskPublisher;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;

    @Value("${services.blockchain-api.url:http://localhost:8080}")
    private String blockchainApiUrl;

    public ConsensusService(BlockService blockService,
            TaskPublisher taskPublisher,
            RestTemplate restTemplate,
            RedisTemplate<String, Object> redis,
            ObjectMapper objectMapper) {
        this.blockService = blockService;
        this.taskPublisher = taskPublisher;
        this.restTemplate = restTemplate;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void registerTask(MiningTask task) {
        redis.opsForValue().set(TASK_KEY_PREFIX + task.taskId(), task, TASK_TTL);
        log.debug("Tarea registrada: {}", task.taskId());
    }

    public void processResult(MiningResultEvent result) {
        if (!result.success()) {
            log.debug("Worker {} no encontró nonce en su rango para taskId={}",
                    result.workerId(), result.taskId());
            return;
        }

        // CAS distribuido a nivel de BLOQUE, no de chunk: con varios
        // chunks por bloque, más de uno podría encontrar un nonce
        // válido casi al mismo tiempo (más probable cuanto más fácil
        // el prefijo). Indexar el CAS por blockIndex asegura que solo
        // el primer chunk en llegar -sea cual sea- confirma el bloque;
        // el resto se descarta aunque también sea una solución válida.
        String resolvedKey = RESOLVED_KEY_PREFIX + "block:" + result.blockIndex();
        // SET NX: cualquier réplica que llegue primero gana; el resto
        // descarta el resultado como tardío/duplicado.
        Boolean wonRace = redis.opsForValue().setIfAbsent(resolvedKey, result.workerId(), TASK_TTL);
        if (!Boolean.TRUE.equals(wonRace)) {
            log.debug("Resultado descartado de worker {} para bloque {} (otro chunk ya lo resolvió)",
                    result.workerId(), result.blockIndex());
            return;
        }

        Object raw = redis.opsForValue().get(TASK_KEY_PREFIX + result.taskId());
        if (raw == null) {
            log.error("Tarea no encontrada en Redis para taskId={}", result.taskId());
            redis.delete(resolvedKey);
            return;
        }
        MiningTask task = (raw instanceof MiningTask mt) ? mt : objectMapper.convertValue(raw, MiningTask.class);

        // Verificar hash (NCT.3)
        String recomputedHash = HashUtils.powHash(result.nonce(), result.str(), result.bcContent());
        if (!recomputedHash.equals(result.blockHash())) {
            log.warn("Hash inválido del worker {}", result.workerId());
            redis.delete(resolvedKey); // libera el CAS: otro resultado puede intentarlo
            return;
        }
        if (!recomputedHash.startsWith(task.prefix())) {
            log.warn("Hash no cumple el prefijo '{}'", task.prefix());
            redis.delete(resolvedKey);
            return;
        }

        // Confirmar bloque (NCT.4)
        Instant now = Instant.now();
        Block confirmed = blockService.confirmBlock(
                task, result.nonce(), result.blockHash(), result.workerId(), now);

        redis.delete(TASK_KEY_PREFIX + result.taskId());

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