package com.rentflow.common.ratelimit;

import com.rentflow.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    private StringRedisTemplate redisTemplate;
    private RateLimitProperties properties;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        properties = new RateLimitProperties();
        service = new RateLimitService(
                redisTemplate,
                properties,
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void consumeBookingCreateAllowsWhenRedisScriptReturnsZero() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        service.consumeBookingCreate(java.util.UUID.fromString("44444444-4444-4444-8444-444444444444"));

        verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void checkLoginAllowedThrowsWhenLimitIsExceeded() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(30L);

        assertThatThrownBy(() -> service.checkLoginAllowed("USER@example.com", "203.0.113.10"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many requests. Please retry later.");
    }

    @Test
    void consumePublicEndpointThrowsWhenLimitIsExceeded() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(45L);

        assertThatThrownBy(() -> service.consumePublicEndpoint("203.0.113.20"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many requests. Please retry later.");
    }

    @Test
    void consumePublicEndpointNormalizesIpForBucketKey() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        service.consumePublicEndpoint(" 203.0.113.50 ");

        verify(redisTemplate).execute(any(DefaultRedisScript.class), eq(List.of("rl:public:203.0.113.50")), any(Object[].class));
    }

    @Test
    void consumePublicEndpointBlankIpFallsBackToUnknownBucket() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        service.consumePublicEndpoint("   ");

        verify(redisTemplate).execute(any(DefaultRedisScript.class), eq(List.of("rl:public:unknown")), any(Object[].class));
    }

    @Test
    void checkLoginAllowedNormalizesEmailAndIpForBucketKey() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        service.checkLoginAllowed(" USER@Example.COM ", " 203.0.113.99 ");

        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(List.of("rl:login:user@example.com:203.0.113.99")), any(Object[].class));
    }

    @Test
    void disabledRateLimiterDoesNotCallRedis() {
        properties.setEnabled(false);

        service.checkLoginAllowed("user@example.com", "203.0.113.10");
        service.recordLoginFailure("user@example.com", "203.0.113.10");
        service.consumeBookingCreate(java.util.UUID.fromString("44444444-4444-4444-8444-444444444444"));
        service.consumePublicEndpoint("203.0.113.10");

        verify(redisTemplate, org.mockito.Mockito.never())
                .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
    }
}
