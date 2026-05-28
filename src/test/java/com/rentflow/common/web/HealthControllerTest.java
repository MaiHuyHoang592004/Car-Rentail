package com.rentflow.common.web;

import com.rentflow.common.config.SecurityConfig;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.security.JwtAuthenticationEntryPoint;
import com.rentflow.common.security.JwtAuthenticationFilter;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.common.security.JsonAccessDeniedHandler;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.security.SecurityContextImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HealthController.class)
@Import({HealthControllerTest.TestConfig.class, SecurityConfig.class})
class HealthControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public CorrelationIdFilter correlationIdFilter() {
            return new CorrelationIdFilter();
        }

        @Bean
        public CorrelationIdHelper correlationIdHelper() {
            return new CorrelationIdHelper();
        }

        @Bean
        public JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider(
                    "test-jwt-secret-key-for-unit-tests-only-minimum-60-bytes-required-for-hs512",
                    Duration.ofMinutes(15),
                    Duration.ofDays(7)
            );
        }

        @Bean
        public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
            return new JwtAuthenticationEntryPoint(new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Bean
        public JsonAccessDeniedHandler jsonAccessDeniedHandler() {
            return new JsonAccessDeniedHandler(new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(
                JwtTokenProvider tokenProvider,
                JwtAuthenticationEntryPoint entryPoint,
                com.rentflow.auth.repository.AuthUserRepository authUserRepository) {
            return new JwtAuthenticationFilter(tokenProvider, entryPoint, authUserRepository);
        }

        @Bean
        public SecurityContext securityContext() {
            return new SecurityContextImpl();
        }
    }

    @MockBean
    private com.rentflow.auth.repository.AuthUserRepository authUserRepository;

    @MockBean
    private com.rentflow.common.ratelimit.RateLimitService rateLimitService;

    @MockBean
    private com.rentflow.common.security.ClientIpResolver clientIpResolver;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpoint_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("rentflow-api"));
    }

    @Test
    void correlationId_headerIsReturned() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .header("X-Correlation-Id", "test-correlation-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-correlation-123"));
    }

    @Test
    void correlationId_isGeneratedIfMissing() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
