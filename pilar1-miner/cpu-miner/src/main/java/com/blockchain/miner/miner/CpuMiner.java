package com.blockchain.miner.miner;

import com.blockchain.miner.hash.HashUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CpuMiner {

    private final int threadCount;

    public CpuMiner() {
        // Usar todos los núcleos disponibles
        this.threadCount = Runtime.getRuntime().availableProcessors();
    }

    public CpuMiner(int threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * Busca un nonce en el rango [rangeMin, rangeMax] cuyo MD5 comience con prefix.
     * Equivalente al rangeKernel de CUDA pero usando hilos de CPU.
     */
    public MinerResult mine(String cadena, String prefix, long rangeMin, long rangeMax) {
        System.out.printf("Cadena:   \"%s\"%n", cadena);
        System.out.printf("Prefijo:  \"%s\" (%d caracteres)%n", prefix, prefix.length());
        System.out.printf("Rango:    [%d, %d]%n", rangeMin, rangeMax);
        System.out.printf("Hilos:    %d%n", threadCount);
        System.out.println("Buscando nonce...\n");

        if (rangeMin > rangeMax) {
            System.out.println("ERROR: rangeMin no puede ser mayor que rangeMax");
            return new MinerResult(cadena, prefix, -1, null, rangeMin, rangeMax, 0, false);
        }

        long startTime = System.currentTimeMillis();

        // Resultado compartido entre hilos
        AtomicBoolean found = new AtomicBoolean(false);
        AtomicLong foundNonce = new AtomicLong(-1);
        String[] foundHash = new String[1];

        // Dividir el rango entre los hilos
        long totalRange = rangeMax - rangeMin + 1;
        long chunkSize = totalRange / threadCount;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            long chunkStart = rangeMin + (long) t * chunkSize;
            long chunkEnd = (t == threadCount - 1)
                    ? rangeMax
                    : chunkStart + chunkSize - 1;

            futures.add(executor.submit(() -> {
                for (long nonce = chunkStart; nonce <= chunkEnd; nonce++) {
                    // Si otro hilo ya encontró la solución, parar
                    if (found.get())
                        return;

                    String input = cadena + nonce;
                    String hash = HashUtils.md5(input);

                    if (HashUtils.hasPrefix(hash, prefix)) {
                        // Solo el primer hilo en llegar escribe el resultado
                        if (found.compareAndSet(false, true)) {
                            foundNonce.set(nonce);
                            foundHash[0] = hash;
                        }
                        return;
                    }
                }
            }));
        }

        // Esperar a que todos los hilos terminen
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
            }
        }

        executor.shutdown();

        long elapsedMs = System.currentTimeMillis() - startTime;

        if (found.get()) {
            return new MinerResult(
                    cadena, prefix,
                    foundNonce.get(), foundHash[0],
                    rangeMin, rangeMax,
                    elapsedMs, true);
        }

        return new MinerResult(
                cadena, prefix,
                -1, null,
                rangeMin, rangeMax,
                elapsedMs, false);
    }

    /**
     * Búsqueda sin límite superior (equivalente al Hit #5 de CUDA).
     */
    public MinerResult mine(String cadena, String prefix) {
        return mine(cadena, prefix, 0, Integer.MAX_VALUE);
    }
}