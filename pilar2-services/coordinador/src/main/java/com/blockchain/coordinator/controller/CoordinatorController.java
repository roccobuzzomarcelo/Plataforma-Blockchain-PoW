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
     * Body: { "transactions": [...], "prefix": "000" }
     * (el campo "workerCount" que aún puede mandar transaction-pool se
     * ignora: la cantidad de chunks ahora es mining.chunk-count, del
     * lado del coordinator -no depende de mineros GPU conectados-.)
     */
    @PostMapping("/mine-block")
    public ResponseEntity<Map<String, Object>> mineBlock(@RequestBody MineBlockRequest req) {
        log.info("Solicitud de minería recibida: {} txs, prefix={}",
                req.transactions().size(), req.prefix());

        List<MiningTask> tasks = blockService.buildMiningTasks(req.transactions(), req.prefix());

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
                "latestHash", latest != null ? latest.blockHash() : "none"));
    }

    // DTO de entrada
    public record MineBlockRequest(
            List<Transaction> transactions,
            String prefix,
            int workerCount) {
    }
}