package com.blockchain.shared.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

public record Block(
        int index,
        String previousHash,
        List<Transaction> transactions,
        long nonce,
        String blockHash,
        String str, // STR - string base usado para el hash
        String bcContent, // BC_CONT - contenido encadenado de la blockchain
        String prefix, // PREFIX - dificultad usada para minar este bloque
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
        BlockStatus status) {
    public enum BlockStatus {
        PENDING, // esperando ser minado
        MINING, // siendo minado por workers
        CONFIRMED // minado y confirmado
    }

    // Construye el string base para el hash: nonce + str + bcContent
    // Equivalente a: hash(nro + string + blockchain_content) del diagrama
    public String toHashString() {
        return nonce + str + bcContent;
    }

    // Construye el bcContent encadenando el hash del bloque anterior
    public static String buildBcContent(String previousHash, List<Transaction> transactions) {
        StringBuilder sb = new StringBuilder();
        sb.append(previousHash);
        transactions.forEach(tx -> sb.append(tx.toHashString()));
        return sb.toString();
    }

    // Construye el STR base (sin nonce) que se envía a los workers
    public static String buildStr(int blockIndex, List<Transaction> transactions) {
        StringBuilder sb = new StringBuilder();
        sb.append(blockIndex);
        transactions.forEach(tx -> sb.append(tx.toHashString()));
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("Block{index=%d, hash=%s, txs=%d, prefix=%s, status=%s}",
                index, blockHash, transactions.size(), prefix, status);
    }
}