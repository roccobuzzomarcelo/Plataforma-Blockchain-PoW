package com.blockchain.txpool.service;

import com.blockchain.shared.model.MiningTask;
import com.blockchain.shared.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class BlockSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(BlockSchedulerService.class);

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
    }

    // DTO que coincide con el MineBlockRequest del coordinator
    public record MineBlockRequest(
            List<Transaction> transactions,
            String prefix,
            int workerCount) {
    }
}