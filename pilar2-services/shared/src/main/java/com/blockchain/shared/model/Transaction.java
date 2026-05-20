package com.blockchain.shared.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record Transaction(
        String id,
        String sender,
        String receiver,
        double amount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
        TransactionType type) {
    public enum TransactionType {
        TRANSFER, // transacción normal entre usuarios
        COINBASE // recompensa al worker ganador
    }

    // Factory para transacción normal
    public static Transaction of(String sender, String receiver, double amount) {
        return new Transaction(
                UUID.randomUUID().toString(),
                sender,
                receiver,
                amount,
                Instant.now(),
                TransactionType.TRANSFER);
    }

    // Factory para recompensa al worker ganador
    public static Transaction coinbase(String workerWinner, double reward) {
        return new Transaction(
                "coinbase-" + UUID.randomUUID(),
                "SYSTEM",
                workerWinner,
                reward,
                Instant.now(),
                TransactionType.COINBASE);
    }

    // Representación para incluir en el hash del bloque
    public String toHashString() {
        return id + sender + receiver + amount + timestamp.toString() + type.name();
    }

    @Override
    public String toString() {
        return String.format("Transaction{%s → %s, %.2f, type=%s, id=%s}",
                sender, receiver, amount, type, id);
    }
}