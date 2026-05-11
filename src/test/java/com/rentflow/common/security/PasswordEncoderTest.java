package com.rentflow.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncoderTest {

    private static final int BCRYPT_STRENGTH = 12;

    @Test
    void passwordEncoder_usesStrength12() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

        String hash = encoder.encode("Password@123");

        assertNotNull(hash);
        assertFalse(hash.isBlank());
        assertTrue(hash.startsWith("$2a$12$") || hash.startsWith("$2b$12$"));
    }

    @Test
    void passwordEncoder_verifiesCorrectPassword() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

        String raw = "MySecret@Pass1";
        String hash = encoder.encode(raw);

        assertTrue(encoder.matches(raw, hash));
    }

    @Test
    void passwordEncoder_rejectsIncorrectPassword() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

        String hash = encoder.encode("CorrectPassword");

        assertFalse(encoder.matches("WrongPassword", hash));
    }

    @Test
    void passwordEncoder_producesDifferentHashesForSamePassword() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

        String hash1 = encoder.encode("SamePassword");
        String hash2 = encoder.encode("SamePassword");

        assertNotEquals(hash1, hash2);
        assertTrue(encoder.matches("SamePassword", hash1));
        assertTrue(encoder.matches("SamePassword", hash2));
    }
}
