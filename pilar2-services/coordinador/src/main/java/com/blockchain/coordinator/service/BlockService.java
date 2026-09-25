package com.blockchain.coordinator.service;

import com.blockchain.shared.model.Block;
import com.blockchain.shared.model.MiningTask;
import com.blockchain.shared.model.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class BlockService {

    private static final Logger log = LoggerFactory.getLogger(BlockService.class);

    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;

    @Value("${mining.reward:50.0}")
    private double miningReward;

    @Value("${mining.prefix:000}")
    private String defaultPrefix;

    @Value("${mining.range-size:10000000}")
    private long rangeSize;

    // Cuántos pedazos se divide el espacio de búsqueda por bloque.
    // Independiente de cuántos workers hay realmente conectados -eso lo
    // resuelve RabbitMQ solo, repartiendo la cola entre quien esté
    // libre-. Es el parámetro que varía la sección 3.3 ("tamaños de
    // fragmentación del pool de transacciones").
    @Value("${mining.chunk-count:4}")
    private int chunkCount;

    public BlockService(RedisTemplate<String, Object> redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** Devuelve el último bloque confirmado de Redis. */
    public Block getLatestBlock() {
        Set<Object> indices = redis.opsForSet().members(GenesisService.BLOCKS_KEY);
        if (indices == null || indices.isEmpty())
            return null;

        int maxIndex = indices.stream()
                .mapToInt(o -> Integer.parseInt(o.toString()))
                .max()
                .orElse(0);

        Object raw = redis.opsForValue().get(GenesisService.BLOCK_PREFIX + maxIndex);
        if (raw == null)
            return null;

        // Deserializar manualmente si Jackson devuelve LinkedHashMap
        if (raw instanceof Block block)
            return block;
        return objectMapper.convertValue(raw, Block.class);
    }

    /**
     * Construye los chunks de MiningTask para el siguiente bloque:
     * divide [0, rangeSize) en {@code chunkCount} rangos disjuntos,
     * cada uno con su propio taskId, pero mismo blockIndex/str/
     * bcContent/prefix/previousHash -eso es lo que hace que cualquiera
     * de los chunks, al resolverse, sea una solución válida para EL
     * MISMO bloque.
     */
    public List<MiningTask> buildMiningTasks(List<Transaction> transactions, String prefix) {
        Block latest = getLatestBlock();
        int nextIndex = (latest == null) ? 1 : latest.index() + 1;
        String previousHash = (latest == null) ? "0".repeat(32) : latest.blockHash();

        String effectivePrefix = (prefix != null && !prefix.isBlank()) ? prefix : defaultPrefix;
        int chunks = Math.max(1, chunkCount);
        long chunkSize = rangeSize / chunks;

        List<MiningTask> tasks = new ArrayList<>();
        long start = 0;
        for (int i = 0; i < chunks; i++) {
            long end = (i == chunks - 1) ? chunks * chunkSize : start + chunkSize;
            tasks.add(MiningTask.of(nextIndex, previousHash, transactions, effectivePrefix, start, end));
            start = end;
        }
        return tasks;
    }

    /** Confirma un bloque resuelto. */
    public Block confirmBlock(MiningTask task, long nonce, String blockHash,
            String winnerWorkerId, Instant timestamp) {
        Transaction coinbase = new Transaction(
                "coinbase-" + task.blockIndex(),
                "SYSTEM",
                winnerWorkerId,
                miningReward,
                timestamp,
                Transaction.TransactionType.COINBASE);

        List<Transaction> allTxs = new ArrayList<>(task.transactions());
        allTxs.add(coinbase);

        Block confirmed = new Block(
                task.blockIndex(),
                task.previousHash(),
                allTxs,
                nonce,
                blockHash,
                task.str(),
                task.bcContent(),
                task.prefix(),
                timestamp,
                Block.BlockStatus.CONFIRMED);

        redis.opsForValue().set(GenesisService.BLOCK_PREFIX + task.blockIndex(), confirmed);
        redis.opsForSet().add(GenesisService.BLOCKS_KEY, String.valueOf(task.blockIndex()));

        task.transactions().forEach(tx -> redis.opsForSet().remove("tx:pending", tx.id()));

        log.info("Bloque {} confirmado. Winner: {}, Nonce: {}, Hash: {}",
                task.blockIndex(), winnerWorkerId, nonce, blockHash);
        return confirmed;
    }
}