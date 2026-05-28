package com.rentflow.common.ratelimit;

import com.rentflow.common.exception.RateLimitExceededException;
import com.rentflow.common.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicEndpointRateLimitFilterTest {

    private RateLimitService rateLimitService;
    private ClientIpResolver clientIpResolver;
    private HandlerExceptionResolver exceptionResolver;
    private FilterChain filterChain;
    private PublicEndpointRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        clientIpResolver = mock(ClientIpResolver.class);
        exceptionResolver = mock(HandlerExceptionResolver.class);
        filterChain = mock(FilterChain.class);
        filter = new PublicEndpointRateLimitFilter(rateLimitService, clientIpResolver, exceptionResolver);
    }

    @Test
    void publicListingsGetConsumesRateLimitAndContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");

        filter.doFilter(request, response, filterChain);

        verify(rateLimitService).consumePublicEndpoint("203.0.113.10");
        verify(filterChain).doFilter(request, response);
        verify(exceptionResolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    void nonPublicPathSkipsRateLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(rateLimitService, never()).consumePublicEndpoint(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void contextPathAndTrailingSlashStillMatchPublicScope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rentflow/api/v1/listings/");
        request.setContextPath("/rentflow");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.88");

        filter.doFilter(request, response, filterChain);

        verify(rateLimitService).consumePublicEndpoint("203.0.113.88");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void limitExceededDelegatesToExceptionResolver() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings/abc/availability");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.77");
        doThrow(new RateLimitExceededException(Duration.ofSeconds(30)))
                .when(rateLimitService).consumePublicEndpoint("203.0.113.77");

        filter.doFilter(request, response, filterChain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), eq(null), any(RateLimitExceededException.class));
        verify(filterChain, never()).doFilter(request, response);
    }
}
