package com.blockchain.miner.miner;

public record MinerResult(
                String cadena,
                String prefix,
                long nonce,
                String hash,
                long rangeMin,
                long rangeMax,
                long elapsedMs,
                boolean found) {
        @Override
        public String toString() {
                if (!found) {
                        return String.format(
                                        "No se encontro nonce con prefijo \"%s\" en el rango [%d, %d] [NOT FOUND] %.3f seg",
                                        prefix, rangeMin, rangeMax, elapsedMs / 1000.0);
                }
                return String.format(
                                "Nonce encontrado: %d%n" +
                                                "MD5 resultante:   %s%n" +
                                                "Prefijo buscado:  \"%s\" %n" +
                                                "Tiempo:           %.3f segundos",
                                nonce, hash, prefix, elapsedMs / 1000.0);
        }
}