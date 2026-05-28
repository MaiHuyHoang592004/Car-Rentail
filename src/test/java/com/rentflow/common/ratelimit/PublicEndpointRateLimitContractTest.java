package com.rentflow.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.GlobalExceptionHandler;
import com.rentflow.common.exception.RateLimitExceededException;
import com.rentflow.common.security.ClientIpResolver;
import com.rentflow.common.web.CorrelationIdFilter;
import com.rentflow.common.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.time.Duration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicEndpointRateLimitContractTest {

    private RateLimitService rateLimitService;
    private ClientIpResolver clientIpResolver;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        clientIpResolver = mock(ClientIpResolver.class);
        ObjectMapper objectMapper = new ObjectMapper();
        CorrelationIdHelper correlationIdHelper = new CorrelationIdHelper();
        GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler(correlationIdHelper);

        HandlerExceptionResolver resolver = new HandlerExceptionResolver() {
            @Override
            public ModelAndView resolveException(
                    HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    Object handler,
                    Exception ex) {
                if (!(ex instanceof RateLimitExceededException rateLimitExceededException)) {
                    return null;
                }
                try {
                    ResponseEntity<ErrorResponse> entity =
                            globalExceptionHandler.handleRateLimitExceeded(rateLimitExceededException, request);
                    response.setStatus(entity.getStatusCode().value());
                    entity.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(objectMapper.writeValueAsString(entity.getBody()));
                    return new ModelAndView();
                } catch (Exception writeFailure) {
                    throw new RuntimeException("Failed to serialize rate-limit response", writeFailure);
                }
            }
        };

        PublicEndpointRateLimitFilter filter = new PublicEndpointRateLimitFilter(
                rateLimitService,
                clientIpResolver,
                resolver);

        mockMvc = MockMvcBuilders.standaloneSetup(new PublicTestController())
                .addFilters(new CorrelationIdFilter(), filter)
                .build();
    }

    @Test
    void rateLimitedPublicEndpointReturns429RetryAfterAndErrorBody() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");
        doThrow(new RateLimitExceededException(Duration.ofSeconds(33)))
                .when(rateLimitService).consumePublicEndpoint("203.0.113.10");

        mockMvc.perform(get("/api/v1/listings").header("X-Correlation-Id", "cid-public-1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "33"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Too many requests. Please retry later."))
                .andExpect(jsonPath("$.correlationId").value("cid-public-1"));
    }

    @Test
    void rateLimitedListingDetailEndpointReturns429Contract() throws Exception {
        assertRateLimitedContract("/api/v1/listings/listing-123", "203.0.113.13", 21);
    }

    @Test
    void rateLimitedApiHealthEndpointReturns429Contract() throws Exception {
        assertRateLimitedContract("/api/v1/health", "203.0.113.14", 19);
    }

    @Test
    void rateLimitedActuatorHealthEndpointReturns429Contract() throws Exception {
        assertRateLimitedContract("/actuator/health", "203.0.113.15", 17);
    }

    @Test
    void publicEndpointWithinLimitPassesThrough() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.11");

        mockMvc.perform(get("/api/v1/listings/abc/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(rateLimitService).consumePublicEndpoint("203.0.113.11");
    }

    @Test
    void endpointOutsidePublicScopeBypassesFilter() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(rateLimitService, never()).consumePublicEndpoint(any());
    }

    @Test
    void trailingSlashPublicPathStillRateLimited() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.12");
        doThrow(new RateLimitExceededException(Duration.ofSeconds(10)))
                .when(rateLimitService).consumePublicEndpoint("203.0.113.12");

        mockMvc.perform(get("/api/v1/listings/"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private void assertRateLimitedContract(String path, String clientIp, int retryAfterSeconds) throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn(clientIp);
        doThrow(new RateLimitExceededException(Duration.ofSeconds(retryAfterSeconds)))
                .when(rateLimitService).consumePublicEndpoint(clientIp);

        mockMvc.perform(get(path).header("X-Correlation-Id", "cid-" + retryAfterSeconds))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", Integer.toString(retryAfterSeconds)))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Too many requests. Please retry later."))
                .andExpect(jsonPath("$.correlationId").value("cid-" + retryAfterSeconds));
    }

    @RestController
    static class PublicTestController {

        @GetMapping("/api/v1/listings")
        Map<String, String> listings() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/v1/listings/{id}")
        Map<String, String> listingDetail(@PathVariable String id) {
            return Map.of("status", "ok", "id", id);
        }

        @GetMapping("/api/v1/listings/{id}/availability")
        Map<String, String> listingAvailability(@PathVariable String id) {
            return Map.of("status", "ok", "id", id);
        }

        @GetMapping("/api/v1/health")
        Map<String, String> health() {
            return Map.of("status", "ok");
        }

        @GetMapping("/actuator/health")
        Map<String, String> actuatorHealth() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/v1/bookings/me")
        Map<String, String> privateEndpoint() {
            return Map.of("status", "ok");
        }
    }
}
