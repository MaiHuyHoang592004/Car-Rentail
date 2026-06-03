package com.rentflow.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.BeforeEach;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rentflow")
            .withUsername("rentflow")
            .withPassword("rentflow");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "10000");
        registry.add("spring.datasource.hikari.idle-timeout", () -> "30000");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void resetDatabaseState() {
        // Keep Flyway history and seeded payment bank catalog, reset all runtime data.
        jdbcTemplate.execute("""
                DO $$
                DECLARE
                    stmt text;
                BEGIN
                    SELECT string_agg(
                               format('TRUNCATE TABLE %I.%I RESTART IDENTITY CASCADE;', schemaname, tablename),
                               ' '
                           )
                      INTO stmt
                      FROM pg_tables
                     WHERE schemaname = 'public'
                       AND tablename NOT IN ('flyway_schema_history', 'payment_banks');

                    IF stmt IS NOT NULL THEN
                        EXECUTE stmt;
                    END IF;
                END $$;
                """);

        if (redisTemplate != null) {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        }
    }
}
