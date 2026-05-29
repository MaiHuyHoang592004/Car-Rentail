package com.rentflow.file.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "rentflow.file.signed-url")
public class FileSignedUrlProperties {

    private Duration ttl = Duration.ofMinutes(10);
    private String baseUrl = "https://files.local";
    private String secret;

    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "rentflow.file.signed-url.secret must be configured and must not be blank");
        }
        if ("change-me".equals(secret)) {
            throw new IllegalStateException(
                    "rentflow.file.signed-url.secret must not use the insecure default value 'change-me'");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "rentflow.file.signed-url.secret must be at least 32 characters");
        }
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret == null ? null : secret.trim();
    }
}
