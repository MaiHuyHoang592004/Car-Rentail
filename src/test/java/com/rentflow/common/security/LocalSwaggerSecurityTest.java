package com.rentflow.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:local-swagger-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "jwt.secret=test-jwt-secret-key-for-unit-tests-only-minimum-60-bytes-required-for-hs512",
        "rentflow.scheduler.void-retry.enabled=false",
        "rentflow.scheduler.outbox-publisher.enabled=false",
        "rentflow.scheduler.idempotency-cleanup.enabled=false",
        "rentflow.file.signed-url.secret=test-signed-url-secret-1234567890-abcdef",
        "encryption.secret-key=Q6iaj8bzwS3UjXZTvgin7MChtBS6lhUZmj19bHF6z1o=",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
class LocalSwaggerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_arePublicInLocal() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerUi_isPublicInLocal() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isFound());
    }
}
