package com.blockchain.txpool.controller;

import com.blockchain.shared.model.Transaction;
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

    public TransactionController(PoolService poolService) {
        this.poolService = poolService;
    }

    // POST /api/pool/transactions - recibe tx del blockchain-api
    @PostMapping("/transactions")
    public ResponseEntity<String> receiveTransaction(@RequestBody Transaction tx) {
        log.info("Transacción recibida: {}", tx);
        poolService.addTransaction(tx);
        return ResponseEntity.ok("Transacción agregada al pool: " + tx.id());
    }

    // GET /api/pool/transactions - lista txs pendientes
    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getPendingTransactions() {
        return ResponseEntity.ok(poolService.getPendingTransactions());
    }

    // GET /api/pool/status - estado del pool
    @GetMapping("/status")
    public ResponseEntity<PoolStatus> getStatus() {
        return ResponseEntity.ok(new PoolStatus(poolService.getPendingCount()));
    }

    public record PoolStatus(long pendingTransactions) {
    }
}