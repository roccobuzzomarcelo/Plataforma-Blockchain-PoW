package com.blockchain.api.service;

import com.blockchain.shared.model.Block;
import com.blockchain.shared.model.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    private static final String BLOCK_KEY_PREFIX = "block:";
    private static final String BLOCKS_INDEX_KEY = "blockchain:blocks";
    private static final String TX_KEY_PREFIX = "tx:";
    private static final String TX_PENDING_KEY = "tx:pending";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public BlockchainService(RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Block> getAllBlocks() {
        Set<Object> indices = redisTemplate.opsForSet().members(BLOCKS_INDEX_KEY);
        if (indices == null || indices.isEmpty())
            return List.of();

        List<Block> blocks = new ArrayList<>();
        for (Object idx : indices) {
            Object raw = redisTemplate.opsForValue().get(BLOCK_KEY_PREFIX + idx);
            if (raw == null)
                continue;
            try {
                Block block = (raw instanceof Block b) ? b
                        : objectMapper.convertValue(raw, Block.class);
                blocks.add(block);
            } catch (Exception e) {
                log.error("Error deserializando bloque {}: {}", idx, e.getMessage());
            }
        }

        blocks.sort((a, b) -> Integer.compare(a.index(), b.index()));
        return blocks;
    }

    public Block getBlock(int index) {
        Object raw = redisTemplate.opsForValue().get(BLOCK_KEY_PREFIX + index);
        if (raw == null)
            return null;
        try {
            return (raw instanceof Block b) ? b
                    : objectMapper.convertValue(raw, Block.class);
        } catch (Exception e) {
            log.error("Error deserializando bloque {}: {}", index, e.getMessage());
            return null;
        }
    }

    public Block getLatestBlock() {
        List<Block> blocks = getAllBlocks();
        return blocks.isEmpty() ? null : blocks.getLast();
    }

    public List<Transaction> getPendingTransactions() {
        Set<Object> txIds = redisTemplate.opsForSet().members(TX_PENDING_KEY);
        if (txIds == null || txIds.isEmpty())
            return List.of();

        List<Transaction> txs = new ArrayList<>();
        for (Object id : txIds) {
            Object raw = redisTemplate.opsForValue().get(TX_KEY_PREFIX + id);
            if (raw == null)
                continue;
            try {
                Transaction tx = (raw instanceof Transaction t) ? t
                        : objectMapper.convertValue(raw, Transaction.class);
                txs.add(tx);
            } catch (Exception e) {
                log.error("Error deserializando transacción {}: {}", id, e.getMessage());
            }
        }
        return txs;
    }

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