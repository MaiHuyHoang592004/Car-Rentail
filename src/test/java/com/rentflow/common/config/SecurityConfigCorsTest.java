package com.rentflow.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigCorsTest {

    @Test
    void corsConfiguration_acceptsExplicitOriginsWithCredentials() {
        SecurityConfig config = new SecurityConfig(
                null,
                null,
                null,
                "http://localhost:3000,http://localhost:3001,http://localhost:3002, https://rentflow.example");

        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/users/me"));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins())
                .containsExactly(
                        "http://localhost:3000",
                        "http://localhost:3001",
                        "http://localhost:3002",
                        "https://rentflow.example");
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void corsConfiguration_allowsLocalFrontendDevPorts() {
        SecurityConfig config = new SecurityConfig(
                null,
                null,
                null,
                "http://localhost:3000,http://localhost:3001,http://localhost:3002");
        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("POST", "/api/v1/auth/verify-email"));

        assertThat(cors).isNotNull();
        assertThat(cors.checkOrigin("http://localhost:3001")).isEqualTo("http://localhost:3001");
        assertThat(cors.checkOrigin("http://localhost:3002")).isEqualTo("http://localhost:3002");
    }

    @Test
    void corsConfiguration_rejectsUnlistedOrigin() {
        SecurityConfig config = new SecurityConfig(
                null,
                null,
                null,
                "http://localhost:3000,http://localhost:3001,http://localhost:3002");
        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("POST", "/api/v1/auth/verify-email"));

        assertThat(cors).isNotNull();
        assertThat(cors.checkOrigin("http://localhost:3999")).isNull();
    }

    @Test
    void corsConfiguration_rejectsWildcardOriginsWithCredentials() {
        SecurityConfig config = new SecurityConfig(null, null, null, "*");

        assertThatThrownBy(config::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not contain '*'");
    }

    @Test
    void corsConfiguration_rejectsBlankOriginConfiguration() {
        SecurityConfig config = new SecurityConfig(null, null, null, " , ");

        assertThatThrownBy(config::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one explicit origin");
    }
}
