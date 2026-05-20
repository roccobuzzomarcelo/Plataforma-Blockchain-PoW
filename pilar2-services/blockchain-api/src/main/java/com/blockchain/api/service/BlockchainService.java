package com.blockchain.api.service;

import com.blockchain.shared.model.Block;
import com.blockchain.shared.model.Transaction;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class BlockchainService {

    private static final String BLOCK_KEY_PREFIX = "block:";
    private static final String BLOCKS_INDEX_KEY = "blockchain:blocks";
    private static final String TX_KEY_PREFIX = "tx:";
    private static final String TX_PENDING_KEY = "tx:pending";

    private final RedisTemplate<String, Object> redisTemplate;

    public BlockchainService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Obtener todos los bloques confirmados ordenados por índice
    public List<Block> getAllBlocks() {
        Set<Object> blockKeys = redisTemplate.opsForSet().members(BLOCKS_INDEX_KEY);
        if (blockKeys == null || blockKeys.isEmpty())
            return List.of();

        List<Block> blocks = new ArrayList<>();
        for (Object key : blockKeys) {
            Object raw = redisTemplate.opsForValue().get(BLOCK_KEY_PREFIX + key);
            if (raw instanceof Block block)
                blocks.add(block);
        }

        blocks.sort((a, b) -> Integer.compare(a.index(), b.index()));
        return blocks;
    }

    // Obtener un bloque por índice
    public Block getBlock(int index) {
        Object raw = redisTemplate.opsForValue().get(BLOCK_KEY_PREFIX + index);
        return raw instanceof Block block ? block : null;
    }

    // Obtener el último bloque confirmado
    public Block getLatestBlock() {
        List<Block> blocks = getAllBlocks();
        return blocks.isEmpty() ? null : blocks.getLast();
    }

    // Obtener transacciones pendientes
    public List<Transaction> getPendingTransactions() {
        Set<Object> txIds = redisTemplate.opsForSet().members(TX_PENDING_KEY);
        if (txIds == null || txIds.isEmpty())
            return List.of();

        List<Transaction> txs = new ArrayList<>();
        for (Object id : txIds) {
            Object raw = redisTemplate.opsForValue().get(TX_KEY_PREFIX + id);
            if (raw instanceof Transaction tx)
                txs.add(tx);
        }
        return txs;
    }

    // Obtener estadísticas de la blockchain
    public BlockchainStats getStats() {
        Long blockCount = redisTemplate.opsForSet().size(BLOCKS_INDEX_KEY);
        Long pendingTxCount = redisTemplate.opsForSet().size(TX_PENDING_KEY);
        Block latest = getLatestBlock();
        return new BlockchainStats(
                blockCount != null ? blockCount : 0,
                pendingTxCount != null ? pendingTxCount : 0,
                latest != null ? latest.index() : -1,
                latest != null ? latest.blockHash() : "none");
    }

    public record BlockchainStats(
            long totalBlocks,
            long pendingTransactions,
            int latestBlockIndex,
            String latestBlockHash) {
    }
}