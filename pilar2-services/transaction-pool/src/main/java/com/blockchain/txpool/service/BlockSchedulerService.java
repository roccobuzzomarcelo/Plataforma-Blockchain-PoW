package com.blockchain.txpool.service;

import com.blockchain.shared.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Con 2+ réplicas del transaction-pool, cada una corre su propio @Scheduled
 * cada 60s (y además el endpoint /flush puede dispararlo manualmente en
 * cualquiera de ellas). Sin coordinación, dos réplicas podrían armar el
 * mismo bloque dos veces. Un lock corto en Redis (SET NX EX) asegura que
 * un solo ciclo de procesamiento corra a la vez, sin importar qué réplica
 * ni qué disparador lo inició.
 */
@Service
public class BlockSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(BlockSchedulerService.class);
    private static final String PROCESSING_LOCK_KEY = "lock:block-processing";

    private final PoolService poolService;
    private final SplitService splitService;
    private final MinerMonitorService minerMonitor;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${services.coordinator.url:http://localhost:8081}")
    private String coordinatorUrl;

    @Value("${pool.default-prefix:000}")
    private String defaultPrefix;

    public BlockSchedulerService(
            PoolService poolService,
            SplitService splitService,
            MinerMonitorService minerMonitor,
            RestTemplate restTemplate,
            StringRedisTemplate stringRedisTemplate) {
        this.poolService = poolService;
        this.splitService = splitService;
        this.minerMonitor = minerMonitor;
        this.restTemplate = restTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Scheduled(fixedDelayString = "${pool.block-interval:60}000")
    public void processBlock() {
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(PROCESSING_LOCK_KEY, "locked", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Otra réplica ya está procesando este ciclo. Se omite.");
            return;
        }

        try {
            List<Transaction> pending = poolService.getPendingTransactions();

            if (pending.isEmpty()) {
                log.info("No hay transacciones pendientes, esperando...");
                return;
            }

            log.info("Procesando {} transacciones pendientes", pending.size());

            boolean gpuAvailable = minerMonitor.hasActiveGpuMiners();
            String prefix = splitService.adjustDifficulty(gpuAvailable, defaultPrefix);
            int workerCount = Math.max(1, (int) minerMonitor.getActiveGpuMinerCount());

            // Armar el request para el coordinator
            MineBlockRequest request = new MineBlockRequest(pending, prefix, workerCount);

            try {
                restTemplate.postForEntity(
                        coordinatorUrl + "/api/coordinator/mine-block",
                        request,
                        Void.class);
                log.info("Bloque enviado al coordinator: {} txs, prefix={}", pending.size(), prefix);
                poolService.clearPendingTransactions();
            } catch (Exception e) {
                log.error("Error enviando tarea al coordinator: {}", e.getMessage());
                // NO limpiar el pool si falló el envío
            }
        } finally {
            stringRedisTemplate.delete(PROCESSING_LOCK_KEY);
        }
    }

    // DTO que coincide con el MineBlockRequest del coordinator
    public record MineBlockRequest(
            List<Transaction> transactions,
            String prefix,
            int workerCount) {
    }
}