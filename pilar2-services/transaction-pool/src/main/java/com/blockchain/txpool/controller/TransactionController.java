package com.blockchain.txpool.controller;

import com.blockchain.shared.model.Transaction;
import com.blockchain.txpool.service.BlockSchedulerService;
import com.blockchain.txpool.service.PoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pool")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final PoolService poolService;
    private final BlockSchedulerService blockSchedulerService;

    // Endpoints de prueba (/test/*): apagados salvo que se active
    // POOL_TEST_ENDPOINTS=true. nginx expone /api/pool/ al navegador, por
    // eso quedan deshabilitados por defecto.
    @Value("${pool.test-endpoints:false}")
    private boolean testEndpoints;

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
    // Parámetros opcionales (pruebas 3.3): prefix exacto, cantidad de chunks
    // y tamaño total del rango de nonces de este bloque.
    @PostMapping("/flush")
    public ResponseEntity<String> flush(
            @RequestParam(name = "prefix", required = false) String prefix,
            @RequestParam(name = "chunks", required = false) Integer chunks,
            @RequestParam(name = "range", required = false) Long range) {
        long pending = poolService.getPendingCount();
        if (pending == 0) {
            return ResponseEntity.ok("No hay transacciones pendientes");
        }
        blockSchedulerService.processBlock(prefix, chunks, range);
        return ResponseEntity.ok("Flush ejecutado: " + pending + " transacciones enviadas al coordinator");
    }

    @PostMapping("/test/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestParam(name = "count") int count) {
        if (!testEndpoints) {
            return ResponseEntity.notFound().build();
        }
        long t0 = System.nanoTime();
        poolService.generateTransactions(count);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        return ResponseEntity.ok(Map.of("generated", count, "ms", ms));
    }

    @PostMapping("/test/clear")
    public ResponseEntity<String> clear() {
        if (!testEndpoints) {
            return ResponseEntity.notFound().build();
        }
        poolService.clearPendingTransactions();
        return ResponseEntity.ok("Pool limpiado");
    }

    @GetMapping("/status")
    public ResponseEntity<PoolStatus> getStatus() {
        return ResponseEntity.ok(new PoolStatus(poolService.getPendingCount()));
    }

    public record PoolStatus(long pendingTransactions) {
    }
}