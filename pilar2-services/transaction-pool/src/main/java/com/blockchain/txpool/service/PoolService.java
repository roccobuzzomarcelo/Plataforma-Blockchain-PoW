package com.blockchain.txpool.service;

import com.blockchain.shared.model.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class PoolService {

    private static final Logger log = LoggerFactory.getLogger(PoolService.class);

    private static final String TX_KEY_PREFIX = "tx:";
    private static final String TX_PENDING_KEY = "tx:pending";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public PoolService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void addTransaction(Transaction tx) {
        try {
            String json = objectMapper.writeValueAsString(tx);
            stringRedisTemplate.opsForValue().set(TX_KEY_PREFIX + tx.id(), json);
            stringRedisTemplate.opsForSet().add(TX_PENDING_KEY, tx.id());
            log.info("Transacción agregada al pool: {}", tx);
        } catch (Exception e) {
            log.error("Error guardando transacción en Redis: {}", e.getMessage());
            throw new RuntimeException("Error guardando transacción", e);
        }
    }

    public List<Transaction> getPendingTransactions() {
        Set<String> txIds = stringRedisTemplate.opsForSet().members(TX_PENDING_KEY);
        if (txIds == null || txIds.isEmpty())
            return List.of();

        List<Transaction> txs = new ArrayList<>();
        for (String id : txIds) {
            String json = stringRedisTemplate.opsForValue().get(TX_KEY_PREFIX + id);
            if (json != null) {
                try {
                    Transaction tx = objectMapper.readValue(json, Transaction.class);
                    txs.add(tx);
                } catch (Exception e) {
                    log.error("Error deserializando transacción {}: {}", id, e.getMessage());
                }
            }
        }
        return txs;
    }

    public void clearPendingTransactions() {
        Set<String> txIds = stringRedisTemplate.opsForSet().members(TX_PENDING_KEY);
        if (txIds != null) {
            for (String id : txIds) {
                stringRedisTemplate.delete(TX_KEY_PREFIX + id);
            }
        }
        stringRedisTemplate.delete(TX_PENDING_KEY);
        log.info("Pool de transacciones limpiado");
    }

    public long getPendingCount() {
        Long count = stringRedisTemplate.opsForSet().size(TX_PENDING_KEY);
        return count != null ? count : 0;
    }
}