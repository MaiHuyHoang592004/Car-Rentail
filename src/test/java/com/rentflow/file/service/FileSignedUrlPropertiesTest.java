package com.rentflow.file.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSignedUrlPropertiesTest {

    @Test
    void validateRejectsBlankSecret() {
        FileSignedUrlProperties properties = new FileSignedUrlProperties();
        properties.setSecret("   ");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be configured");
    }

    @Test
    void validateRejectsInsecureDefaultSecret() {
        FileSignedUrlProperties properties = new FileSignedUrlProperties();
        properties.setSecret("change-me");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not use the insecure default");
    }

    @Test
    void validateAcceptsStrongSecret() {
        FileSignedUrlProperties properties = new FileSignedUrlProperties();
        properties.setTtl(Duration.ofMinutes(10));
        properties.setBaseUrl("https://files.test.local");
        properties.setSecret("test-signed-url-secret-1234567890-abcdef");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
