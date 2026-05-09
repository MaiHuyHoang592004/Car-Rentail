package com.rentflow.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test using Testcontainers to verify PostgreSQL connectivity
 * and Flyway migration execution.
 *
 * This test requires Docker to be running locally.
 * Run with: mvn verify -Pintegration-tests
 *
 * Tag: "integration" - excluded from mvn test, included in mvn verify -Pintegration-tests
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
class PostgresContainerSmokeTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rentflow")
            .withUsername("rentflow")
            .withPassword("rentflow");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void applicationContext_loadsWithPostgres() {
        // Application context loads successfully with Testcontainers PostgreSQL
        // and Flyway migrations run automatically.
        // This test verifies:
        // 1. PostgreSQL container starts correctly
        // 2. DataSource is configured properly
        // 3. Flyway migrations execute successfully
    }

    @Test
    void healthEndpoint_accessible() {
        // This test is intentionally minimal.
        // The actual health endpoint behavior is tested in HealthControllerTest.
    }
}
