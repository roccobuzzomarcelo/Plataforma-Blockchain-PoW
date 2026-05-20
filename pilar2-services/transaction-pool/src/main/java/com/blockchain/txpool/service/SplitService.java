package com.blockchain.txpool.service;

import com.blockchain.shared.model.MiningTask;
import com.blockchain.shared.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SplitService {

    private static final Logger log = LoggerFactory.getLogger(SplitService.class);

    @Value("${pool.range-size:1000000}")
    private long rangeSize;

    @Value("${pool.default-prefix:000}")
    private String defaultPrefix;

    // Crea una lista de MiningTasks dividiendo el espacio de búsqueda
    // en rangos del tamaño configurado
    public List<MiningTask> splitIntoTasks(
            int blockIndex,
            String previousHash,
            List<Transaction> transactions,
            int workerCount) {

        List<MiningTask> tasks = new ArrayList<>();
        long start = 0;

        for (int i = 0; i < workerCount; i++) {
            long end = start + rangeSize - 1;
            MiningTask task = MiningTask.of(
                    blockIndex,
                    previousHash,
                    transactions,
                    defaultPrefix,
                    start,
                    end);
            tasks.add(task);
            log.info("Tarea creada: {} rango=[{},{}]", task.taskId(), start, end);
            start = end + 1;
        }

        return tasks;
    }

    // Ajusta la dificultad según disponibilidad de GPU
    public String adjustDifficulty(boolean gpuAvailable, String currentPrefix) {
        if (!gpuAvailable && currentPrefix.length() > 2) {
            String newPrefix = currentPrefix.substring(0, currentPrefix.length() - 1);
            log.warn("Sin GPU disponible, reduciendo dificultad: {} → {}",
                    currentPrefix, newPrefix);
            return newPrefix;
        }
        return currentPrefix;
    }
}