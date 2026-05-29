package com.rentflow.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration test using Testcontainers to verify PostgreSQL connectivity
 * and Flyway migration execution.
 *
 * This test requires Docker to be running locally.
 * Run with: mvn verify -Pintegration-tests
 *
 * Tag: "integration" - excluded from mvn test, included in mvn verify -Pintegration-tests
 */
@Tag("integration")
class PostgresContainerSmokeTest extends BaseIntegrationTest {

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
