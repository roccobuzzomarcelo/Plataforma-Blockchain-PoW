package com.blockchain.shared.event;

public record MiningResultEvent(
        String taskId,
        String workerId,
        int blockIndex,
        long nonce,
        String blockHash, // hash(nonce + str + bcContent)
        String str, // STR usado para calcular el hash
        String bcContent, // BC_CONT usado para calcular el hash
        boolean success, // false si no encontró nada en el rango
        long elapsedMs) {
    @Override
    public String toString() {
        return String.format(
                "MiningResult{taskId=%s, worker=%s, block=%d, nonce=%d, success=%s, time=%dms}",
                taskId, workerId, blockIndex, nonce, success, elapsedMs);
    }
}