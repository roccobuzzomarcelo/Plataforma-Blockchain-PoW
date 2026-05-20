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

    private static final String BLOCK_COUNTER_KEY = "blockchain:next_index";
    private static final String LAST_HASH_KEY = "blockchain:last_hash";
    private static final String GENESIS_HASH = "0".repeat(64);

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

        int blockIndex = getNextBlockIndex();
        String lastHash = getLastBlockHash();

        int workerCount = Math.max(1, (int) minerMonitor.getActiveGpuMinerCount());

        List<MiningTask> tasks = splitService.splitIntoTasks(
                blockIndex, lastHash, pending, workerCount);

        for (MiningTask task : tasks) {
            try {
                restTemplate.postForEntity(
                        coordinatorUrl + "/api/coordinator/tasks",
                        task,
                        Void.class);
                log.info("Tarea enviada al coordinator: {}", task);
            } catch (Exception e) {
                log.error("Error enviando tarea al coordinator: {}", e.getMessage());
            }
        }

        poolService.clearPendingTransactions();
    }

    private int getNextBlockIndex() {
        String index = stringRedisTemplate.opsForValue().get(BLOCK_COUNTER_KEY);
        return index != null ? Integer.parseInt(index) : 1;
    }

    private String getLastBlockHash() {
        String hash = stringRedisTemplate.opsForValue().get(LAST_HASH_KEY);
        return hash != null ? hash : GENESIS_HASH;
    }
}