package com.blockchain.coordinator.service;

import com.blockchain.shared.model.Block;
import com.blockchain.shared.model.Transaction;
import com.blockchain.shared.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Crea el bloque génesis si Redis no tiene ningún bloque.
 * Se ejecuta al arrancar el Coordinator.
 */
@Service
public class GenesisService {

    private static final Logger log = LoggerFactory.getLogger(GenesisService.class);
    static final String BLOCKS_KEY = "blockchain:blocks";
    static final String BLOCK_PREFIX = "block:";

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
        createGenesis();
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