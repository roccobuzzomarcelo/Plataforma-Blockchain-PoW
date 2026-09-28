package com.blockchain.coordinator.controller;

import com.blockchain.coordinator.service.BlockService;
import com.blockchain.coordinator.service.ConsensusService;
import com.blockchain.coordinator.messaging.TaskPublisher;
import com.blockchain.shared.model.Block;
import com.blockchain.shared.model.MiningTask;
import com.blockchain.shared.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorController {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorController.class);

    private final BlockService blockService;
    private final ConsensusService consensusService;
    private final TaskPublisher taskPublisher;

    public CoordinatorController(BlockService blockService,
            ConsensusService consensusService,
            TaskPublisher taskPublisher) {
        this.blockService = blockService;
        this.consensusService = consensusService;
        this.taskPublisher = taskPublisher;
    }

    /**
     * Recibe un bloque formado del Transaction Pool (NCT.1).
     * Body: { "transactions": [...], "prefix": "000",
     *         "chunkCount": 3, "rangeSize": 10000000 }
     * chunkCount y rangeSize son opcionales (null = valores configurados
     * en mining.chunk-count / mining.range-size); sirven para variar la
     * fragmentación y el rango en las pruebas de la sección 3.3.
     * (El campo "workerCount" que aún manda transaction-pool se ignora:
     * la cantidad de chunks no depende de mineros GPU conectados.)
     */
    @PostMapping("/mine-block")
    public ResponseEntity<Map<String, Object>> mineBlock(@RequestBody MineBlockRequest req) {
        log.info("Solicitud de minería recibida: {} txs, prefix={}",
                req.transactions().size(), req.prefix());

        List<MiningTask> tasks = blockService.buildMiningTasks(
                req.transactions(), req.prefix(), req.chunkCount(), req.rangeSize());

        for (MiningTask task : tasks) {
            consensusService.registerTask(task);
            taskPublisher.publishTask(task);
        }

        MiningTask first = tasks.get(0);
        MiningTask last = tasks.get(tasks.size() - 1);
        return ResponseEntity.ok(Map.of(
                "blockIndex", first.blockIndex(),
                "prefix", first.prefix(),
                "chunks", tasks.size(),
                "rangeMin", first.rangeMin(),
                "rangeMax", last.rangeMax()));
    }

    /** Estado general del coordinator. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Block latest = blockService.getLatestBlock();
        return ResponseEntity.ok(Map.of(
                "service", "coordinator",
                "latestBlock", latest != null ? latest.index() : -1,
                "latestHash", latest != null ? latest.blockHash() : "none",
                // Lo consulta test-load/run_experiments.ps1 para detectar una
                // imagen vieja que ignoraria chunkCount/rangeSize en silencio.
                "supportsOverrides", true));
    }

    // DTO de entrada
    public record MineBlockRequest(
            List<Transaction> transactions,
            String prefix,
            int workerCount,
            Integer chunkCount,
            Long rangeSize) {
    }
}