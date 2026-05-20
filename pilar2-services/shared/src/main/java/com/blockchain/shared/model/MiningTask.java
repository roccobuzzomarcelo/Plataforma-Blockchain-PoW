package com.blockchain.shared.model;

import java.util.List;
import java.util.UUID;

public record MiningTask(
        String taskId,
        int blockIndex,
        String previousHash,
        List<Transaction> transactions,
        String prefix, // dificultad: "000", "0000", etc.
        String str, // STR - string base del bloque
        String bcContent, // BC_CONT - contenido encadenado
        long rangeMin, // rango de búsqueda del nonce
        long rangeMax) {
    // Factory para crear una tarea nueva
    public static MiningTask of(
            int blockIndex,
            String previousHash,
            List<Transaction> transactions,
            String prefix,
            long rangeMin,
            long rangeMax) {
        String str = Block.buildStr(blockIndex, transactions);
        String bcContent = Block.buildBcContent(previousHash, transactions);
        return new MiningTask(
                UUID.randomUUID().toString(),
                blockIndex,
                previousHash,
                transactions,
                prefix,
                str,
                bcContent,
                rangeMin,
                rangeMax);
    }

    // Base del hash que debe resolver el worker:
    // hash(nonce + str + bcContent)
    public String buildHashInput(long nonce) {
        return nonce + str + bcContent;
    }

    @Override
    public String toString() {
        return String.format(
                "MiningTask{id=%s, block=%d, prefix=%s, range=[%d,%d]}",
                taskId, blockIndex, prefix, rangeMin, rangeMax);
    }
}