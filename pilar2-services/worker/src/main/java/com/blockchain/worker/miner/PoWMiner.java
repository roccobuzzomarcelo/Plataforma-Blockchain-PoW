package com.blockchain.worker.miner;

import com.blockchain.shared.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minero CPU multi-hilo.
 * Divide el rango [rangeMin, rangeMax] entre N threads.
 * El primero en encontrar el nonce válido cancela a los demás.
 */
public class PoWMiner {

    private static final Logger log = LoggerFactory.getLogger(PoWMiner.class);

    private final int threadCount;

    public PoWMiner(int threadCount) {
        this.threadCount = threadCount;
    }

    public record MinerResult(long nonce, String hash) {
    }

    /**
     * Busca un nonce en [rangeMin, rangeMax] tal que
     * MD5(nonce + str + bcContent) comience con prefix.
     *
     * @return Optional.empty() si no encontró nada en el rango
     */
    public Optional<MinerResult> mine(String str, String bcContent,
            String prefix, long rangeMin, long rangeMax) {
        long totalRange = rangeMax - rangeMin;
        long chunkSize = totalRange / threadCount;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CompletionService<MinerResult> completion = new ExecutorCompletionService<>(executor);
        AtomicBoolean found = new AtomicBoolean(false);

        for (int i = 0; i < threadCount; i++) {
            long start = rangeMin + (long) i * chunkSize;
            long end = (i == threadCount - 1) ? rangeMax : start + chunkSize;

            completion.submit(() -> searchRange(str, bcContent, prefix, start, end, found));
        }

        MinerResult result = null;
        try {
            for (int i = 0; i < threadCount; i++) {
                Future<MinerResult> future = completion.take();
                MinerResult r = future.get();
                if (r != null && result == null) {
                    result = r;
                    found.set(true); // cancela los threads restantes
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error durante la minería: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }

        return Optional.ofNullable(result);
    }

    private MinerResult searchRange(String str, String bcContent, String prefix,
            long start, long end, AtomicBoolean found) {
        for (long nonce = start; nonce < end; nonce++) {
            if (found.get())
                return null; // otro thread ya encontró

            String hash = HashUtils.powHash(nonce, str, bcContent);
            if (hash.startsWith(prefix)) {
                log.debug("Nonce encontrado: {} → {}", nonce, hash);
                return new MinerResult(nonce, hash);
            }
        }
        return null;
    }
}