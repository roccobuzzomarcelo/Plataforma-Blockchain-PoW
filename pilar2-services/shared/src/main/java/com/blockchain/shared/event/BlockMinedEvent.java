package com.blockchain.shared.event;

public record BlockMinedEvent(
        int blockIndex,
        String blockHash,
        String winnerWorkerId,
        long nonce,
        String prefix,
        double reward // recompensa entregada al worker ganador
) {
    @Override
    public String toString() {
        return String.format(
                "BlockMined{index=%d, hash=%s, winner=%s, nonce=%d, reward=%.2f}",
                blockIndex, blockHash, winnerWorkerId, nonce, reward);
    }
}