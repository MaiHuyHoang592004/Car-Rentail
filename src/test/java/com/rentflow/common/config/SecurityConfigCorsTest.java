package com.rentflow.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigCorsTest {

    private SecurityConfig configWithOrigins(String origins) {
        return new SecurityConfig(
                null,
                null,
                null,
                origins,
                true,
                true,
                new MockEnvironment().withProperty("spring.profiles.active", "test"));
    }

    @Test
    void corsConfiguration_acceptsExplicitOriginsWithCredentials() {
        SecurityConfig config = configWithOrigins(
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
        SecurityConfig config = configWithOrigins(
                "http://localhost:3000,http://localhost:3001,http://localhost:3002");
        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("POST", "/api/v1/auth/verify-email"));

        assertThat(cors).isNotNull();
        assertThat(cors.checkOrigin("http://localhost:3001")).isEqualTo("http://localhost:3001");
        assertThat(cors.checkOrigin("http://localhost:3002")).isEqualTo("http://localhost:3002");
    }

    @Test
    void corsConfiguration_rejectsUnlistedOrigin() {
        SecurityConfig config = configWithOrigins(
                "http://localhost:3000,http://localhost:3001,http://localhost:3002");
        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("POST", "/api/v1/auth/verify-email"));

        assertThat(cors).isNotNull();
        assertThat(cors.checkOrigin("http://localhost:3999")).isNull();
    }

    @Test
    void corsConfiguration_rejectsWildcardOriginsWithCredentials() {
        SecurityConfig config = configWithOrigins("*");

        assertThatThrownBy(config::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not contain '*'");
    }

    @Test
    void corsConfiguration_rejectsBlankOriginConfiguration() {
        SecurityConfig config = configWithOrigins(" , ");

        assertThatThrownBy(config::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one explicit origin");
    }
}
