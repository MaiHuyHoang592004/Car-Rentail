package com.rentflow.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionUtilTest {

    private static final String VALID_BASE64_KEY = "Q6iaj8bzwS3UjXZTvgin7MChtBS6lhUZmj19bHF6z1o=";

    @Test
    void constructorRejectsMissingKeyOutsideTestProfile() {
        assertThatThrownBy(() -> new EncryptionUtil(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption.secret-key is not configured");
    }

    @Test
    void constructorAcceptsExplicitValidKey() {
        EncryptionUtil util = new EncryptionUtil(VALID_BASE64_KEY, null);

        String encrypted = util.encrypt("hello");
        assertThat(util.decrypt(encrypted)).isEqualTo("hello");
    }
}
