package com.rentflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

/**
 * Lightweight context smoke test for Phase 1.
 * Uses H2 in-memory database to avoid Docker/Testcontainers dependency.
 * This test verifies that the Spring application context can load
 * without requiring a real PostgreSQL database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "rentflow.scheduler.expire-held-bookings.enabled=false"
})
class RentFlowApplicationTests {

    @SpyBean
    private RentFlowApplication application;

    @Test
    void contextLoads() {
        // Application context loads successfully with mocked web environment
        // and H2 in-memory database.
    }
}
