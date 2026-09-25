package com.blockchain.coordinator.service;

import com.blockchain.shared.model.Block;
import com.blockchain.shared.model.Transaction;
import com.blockchain.shared.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Crea el bloque génesis si Redis no tiene ningún bloque.
 * Se ejecuta al arrancar el Coordinator.
 *
 * Con 2+ réplicas del coordinator arrancando a la vez, todas verían Redis
 * vacío en el mismo instante y crearían su propio génesis. Un lock
 * distribuido (SET NX) en Redis asegura que solo una réplica lo cree.
 */
@Service
public class GenesisService {

    private static final Logger log = LoggerFactory.getLogger(GenesisService.class);
    static final String BLOCKS_KEY = "blockchain:blocks";
    static final String BLOCK_PREFIX = "block:";
    private static final String GENESIS_LOCK_KEY = "lock:genesis";

    private final RedisTemplate<String, Object> redis;

    @Value("${mining.prefix:000}")
    private String prefix;

    public GenesisService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public void initializeIfNeeded() {
        Long count = redis.opsForSet().size(BLOCKS_KEY);
        if (count != null && count > 0) {
            log.info("Blockchain existente encontrada ({} bloques). No se crea génesis.", count);
            return;
        }

        Boolean acquired = redis.opsForValue().setIfAbsent(GENESIS_LOCK_KEY, "locked", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("Otra réplica ya está creando el génesis. No se hace nada.");
            return;
        }
        try {
            // Re-chequeo dentro del lock: por si otra réplica terminó de crearlo
            // justo antes de que consiguiéramos el lock.
            Long recount = redis.opsForSet().size(BLOCKS_KEY);
            if (recount != null && recount > 0) {
                log.info("Otra réplica creó el génesis mientras esperábamos el lock.");
                return;
            }
            createGenesis();
        } finally {
            redis.delete(GENESIS_LOCK_KEY);
        }
    }

    private void createGenesis() {
        String previousHash = "0".repeat(32);
        Instant now = Instant.now();

        // Transaction: (id, sender, receiver, amount, timestamp, type)
        Transaction genesisTx = new Transaction(
                "genesis-tx-0", "SYSTEM", "SYSTEM", 0.0,
                now, // timestamp ANTES de type
                Transaction.TransactionType.COINBASE);

        List<Transaction> txs = List.of(genesisTx);

        // El génesis se mina con nonce 0 sin PoW real
        String str = Block.buildStr(0, txs);
        String bcContent = Block.buildBcContent(previousHash, txs);
        String blockHash = HashUtils.powHash(0L, str, bcContent);

        // Block: (index, previousHash, transactions, nonce, blockHash, str, bcContent,
        // prefix, timestamp, status)
        Block genesis = new Block(
                0, previousHash, txs, 0L,
                blockHash, str, bcContent,
                prefix, // prefix ANTES de timestamp
                now, Block.BlockStatus.CONFIRMED);

        // Guardar en Redis
        redis.opsForValue().set(BLOCK_PREFIX + 0, genesis);
        redis.opsForSet().add(BLOCKS_KEY, "0");

        log.info("Bloque génesis creado: {}", genesis);
    }
}