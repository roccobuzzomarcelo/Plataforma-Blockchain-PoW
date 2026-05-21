package com.blockchain.txpool.controller;

import com.blockchain.shared.model.Transaction;
import com.blockchain.txpool.service.BlockSchedulerService;
import com.blockchain.txpool.service.PoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pool")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final PoolService poolService;
    private final BlockSchedulerService blockSchedulerService;

    public TransactionController(PoolService poolService,
            BlockSchedulerService blockSchedulerService) {
        this.poolService = poolService;
        this.blockSchedulerService = blockSchedulerService;
    }

    @PostMapping("/transactions")
    public ResponseEntity<String> receiveTransaction(@RequestBody Transaction tx) {
        log.info("Transacción recibida: {}", tx);
        poolService.addTransaction(tx);
        return ResponseEntity.ok("Transacción agregada al pool: " + tx.id());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getPendingTransactions() {
        return ResponseEntity.ok(poolService.getPendingTransactions());
    }

    // Fuerza el procesamiento inmediato sin esperar el scheduler
    @PostMapping("/flush")
    public ResponseEntity<String> flush() {
        long pending = poolService.getPendingCount();
        if (pending == 0) {
            return ResponseEntity.ok("No hay transacciones pendientes");
        }
        blockSchedulerService.processBlock();
        return ResponseEntity.ok("Flush ejecutado: " + pending + " transacciones enviadas al coordinator");
    }

    @GetMapping("/status")
    public ResponseEntity<PoolStatus> getStatus() {
        return ResponseEntity.ok(new PoolStatus(poolService.getPendingCount()));
    }

    public record PoolStatus(long pendingTransactions) {
    }
}