package com.blockchain.txpool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class MinerMonitorService {

    private static final Logger log = LoggerFactory.getLogger(MinerMonitorService.class);
    private static final String GPU_MINERS_KEY = "miners:gpu";
    private static final String GPU_MINER_PREFIX = "miners:gpu:";
    private static final long GPU_TIMEOUT_SECS = 30;

    private final StringRedisTemplate stringRedisTemplate;

    public MinerMonitorService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void registerKeepAlive(String workerId) {
        String key = GPU_MINER_PREFIX + workerId;
        stringRedisTemplate.opsForValue().set(key, Instant.now().toString());
        stringRedisTemplate.expire(key, GPU_TIMEOUT_SECS, TimeUnit.SECONDS);
        stringRedisTemplate.opsForSet().add(GPU_MINERS_KEY, workerId);
        log.debug("Keep-alive registrado para GPU miner: {}", workerId);
    }

    public boolean hasActiveGpuMiners() {
        Set<String> miners = stringRedisTemplate.opsForSet().members(GPU_MINERS_KEY);
        if (miners == null || miners.isEmpty())
            return false;

        long activeCount = miners.stream()
                .filter(id -> Boolean.TRUE.equals(
                        stringRedisTemplate.hasKey(GPU_MINER_PREFIX + id)))
                .count();

        return activeCount > 0;
    }

    public long getActiveGpuMinerCount() {
        Set<String> miners = stringRedisTemplate.opsForSet().members(GPU_MINERS_KEY);
        if (miners == null)
            return 0;
        return miners.stream()
                .filter(id -> Boolean.TRUE.equals(
                        stringRedisTemplate.hasKey(GPU_MINER_PREFIX + id)))
                .count();
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanInactiveMiners() {
        Set<String> miners = stringRedisTemplate.opsForSet().members(GPU_MINERS_KEY);
        if (miners == null)
            return;

        for (String id : miners) {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(GPU_MINER_PREFIX + id))) {
                stringRedisTemplate.opsForSet().remove(GPU_MINERS_KEY, id);
                log.info("GPU miner removido por inactividad: {}", id);
            }
        }
    }
}