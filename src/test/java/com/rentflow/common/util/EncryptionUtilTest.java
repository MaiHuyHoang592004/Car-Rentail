package com.rentflow.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionUtilTest {

    @Test
    void constructorRejectsMissingKeyOutsideTestProfile() {
        assertThatThrownBy(() -> new EncryptionUtil(null, null, "local"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption.secret-key is not configured");
    }

    @Test
    void constructorAllowsTestProfileFallback() {
        EncryptionUtil util = new EncryptionUtil(null, null, "test");

        String encrypted = util.encrypt("hello");
        assertThat(util.decrypt(encrypted)).isEqualTo("hello");
    }
}
