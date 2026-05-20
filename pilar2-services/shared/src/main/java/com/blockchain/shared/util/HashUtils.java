package com.blockchain.shared.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {

    private HashUtils() {
    }

    public static String md5(String input) {
        return hash(input, "MD5");
    }

    public static String sha256(String input) {
        return hash(input, "SHA-256");
    }

    public static boolean hasPrefix(String hash, String prefix) {
        return hash.startsWith(prefix);
    }

    // Hash del PoW: hash(nonce + str + bcContent)
    // Equivalente al algoritmo del diagrama de los profesores
    public static String powHash(long nonce, String str, String bcContent) {
        return md5(nonce + str + bcContent);
    }

    // Verifica si un nonce resuelve el PoW
    public static boolean isValidPoW(long nonce, String str, String bcContent, String prefix) {
        return hasPrefix(powHash(nonce, str, bcContent), prefix);
    }

    private static String hash(String input, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(input.getBytes());
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(algorithm + " no disponible", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}