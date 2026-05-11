package com.rentflow.auth.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenHashTest {

    @Test
    void sha256_isDeterministic() throws Exception {
        String input = "my-opaque-token-12345";

        String hash1 = sha256(input);
        String hash2 = sha256(input);

        assertEquals(hash1, hash2);
    }

    @Test
    void sha256_producesDifferentHashForDifferentInput() throws Exception {
        String hash1 = sha256("token-a");
        String hash2 = sha256("token-b");

        assertNotEquals(hash1, hash2);
    }

    @Test
    void sha256_doesNotStoreRawToken() throws Exception {
        String rawToken = "super-secret-refresh-token";

        String hash = sha256(rawToken);

        assertFalse(hash.contains(rawToken));
        assertEquals(64, hash.length());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
