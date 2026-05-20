package com.blockchain.api.service;

import com.blockchain.shared.model.Block;
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
public class BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    private static final String BLOCK_KEY_PREFIX = "block:";
    private static final String BLOCKS_INDEX_KEY = "blockchain:blocks";
    private static final String TX_KEY_PREFIX = "tx:";
    private static final String TX_PENDING_KEY = "tx:pending";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public BlockchainService(StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Block> getAllBlocks() {
        Set<String> blockKeys = stringRedisTemplate.opsForSet().members(BLOCKS_INDEX_KEY);
        if (blockKeys == null || blockKeys.isEmpty())
            return List.of();

        List<Block> blocks = new ArrayList<>();
        for (String key : blockKeys) {
            String json = stringRedisTemplate.opsForValue().get(BLOCK_KEY_PREFIX + key);
            if (json != null) {
                try {
                    blocks.add(objectMapper.readValue(json, Block.class));
                } catch (Exception e) {
                    log.error("Error deserializando bloque {}: {}", key, e.getMessage());
                }
            }
        }

        blocks.sort((a, b) -> Integer.compare(a.index(), b.index()));
        return blocks;
    }

    public Block getBlock(int index) {
        String json = stringRedisTemplate.opsForValue().get(BLOCK_KEY_PREFIX + index);
        if (json == null)
            return null;
        try {
            return objectMapper.readValue(json, Block.class);
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
        Set<String> txIds = stringRedisTemplate.opsForSet().members(TX_PENDING_KEY);
        if (txIds == null || txIds.isEmpty())
            return List.of();

        List<Transaction> txs = new ArrayList<>();
        for (String id : txIds) {
            String json = stringRedisTemplate.opsForValue().get(TX_KEY_PREFIX + id);
            if (json != null) {
                try {
                    txs.add(objectMapper.readValue(json, Transaction.class));
                } catch (Exception e) {
                    log.error("Error deserializando transacción {}: {}", id, e.getMessage());
                }
            }
        }
        return txs;
    }

    public BlockchainStats getStats() {
        Long blockCount = stringRedisTemplate.opsForSet().size(BLOCKS_INDEX_KEY);
        Long pendingTxCount = stringRedisTemplate.opsForSet().size(TX_PENDING_KEY);
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