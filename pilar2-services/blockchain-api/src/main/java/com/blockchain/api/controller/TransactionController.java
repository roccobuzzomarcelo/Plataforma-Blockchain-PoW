package com.blockchain.api.controller;

import com.blockchain.shared.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final RestTemplate restTemplate;

    @Value("${services.transaction-pool.url:http://localhost:8082}")
    private String transactionPoolUrl;

    public TransactionController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // POST /api/transactions
    // Recibe una transacción del frontend y la reenvía al transaction-pool
    @PostMapping
    public ResponseEntity<String> submitTransaction(@RequestBody TransactionRequest request) {
        log.info("Recibiendo transacción: {} → {}, amount={}",
                request.sender(), request.receiver(), request.amount());

        // Validaciones básicas
        if (request.sender() == null || request.sender().isBlank()) {
            return ResponseEntity.badRequest().body("El sender no puede estar vacío");
        }
        if (request.receiver() == null || request.receiver().isBlank()) {
            return ResponseEntity.badRequest().body("El receiver no puede estar vacío");
        }
        if (request.amount() <= 0) {
            return ResponseEntity.badRequest().body("El monto debe ser mayor a 0");
        }
        if (request.sender().equals(request.receiver())) {
            return ResponseEntity.badRequest().body("El sender y receiver no pueden ser iguales");
        }

        // Crear la transacción
        Transaction tx = Transaction.of(request.sender(), request.receiver(), request.amount());

        // Reenviar al transaction-pool
        try {
            restTemplate.postForEntity(
                    transactionPoolUrl + "/api/pool/transactions",
                    tx,
                    Void.class);
            log.info("Transacción enviada al pool: {}", tx);
            return ResponseEntity.ok("Transacción enviada: " + tx.id());
        } catch (Exception e) {
            log.error("Error al enviar transacción al pool: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Error al procesar la transacción");
        }
    }

    // DTO de entrada desde el frontend
    public record TransactionRequest(
            String sender,
            String receiver,
            double amount) {
    }
}