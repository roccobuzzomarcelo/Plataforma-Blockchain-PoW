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
     * Body: { "transactions": [...], "prefix": "000", "workerCount": 2 }
     */
    @PostMapping("/mine-block")
    public ResponseEntity<Map<String, Object>> mineBlock(@RequestBody MineBlockRequest req) {
        log.info("Solicitud de minería recibida: {} txs, prefix={}",
                req.transactions().size(), req.prefix());

        MiningTask task = blockService.buildMiningTask(
                req.transactions(),
                req.prefix(),
                req.workerCount() > 0 ? req.workerCount() : 1);

        consensusService.registerTask(task);
        taskPublisher.publishTask(task);

        return ResponseEntity.ok(Map.of(
                "taskId", task.taskId(),
                "blockIndex", task.blockIndex(),
                "prefix", task.prefix(),
                "rangeMin", task.rangeMin(),
                "rangeMax", task.rangeMax()));
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