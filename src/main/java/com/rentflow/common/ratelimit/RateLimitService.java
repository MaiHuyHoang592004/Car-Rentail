package com.rentflow.common.ratelimit;

import com.rentflow.common.exception.RateLimitExceededException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RateLimitService {

    private static final String CHECK_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count >= limit then
              local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
              if oldest[2] then
                return math.max(1, math.ceil((tonumber(oldest[2]) + window - now) / 1000))
              end
              return math.max(1, math.ceil(window / 1000))
            end
            return 0
            """;

    private static final String ADD_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local member = ARGV[3]
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return 0
            """;

    private static final String CONSUME_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count >= limit then
              local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
              if oldest[2] then
                return math.max(1, math.ceil((tonumber(oldest[2]) + window - now) / 1000))
              end
              return math.max(1, math.ceil(window / 1000))
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final Clock clock;
    private final DefaultRedisScript<Long> checkScript = new DefaultRedisScript<>(CHECK_SCRIPT, Long.class);
    private final DefaultRedisScript<Long> addScript = new DefaultRedisScript<>(ADD_SCRIPT, Long.class);
    private final DefaultRedisScript<Long> consumeScript = new DefaultRedisScript<>(CONSUME_SCRIPT, Long.class);

    public RateLimitService(StringRedisTemplate redisTemplate, RateLimitProperties properties, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    public void checkLoginAllowed(String email, String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }
        RateLimitProperties.Login login = properties.getLogin();
        assertAllowed(loginKey(email, clientIp), login.getLimit(), login.getWindow(), false);
    }

    public void recordLoginFailure(String email, String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }
        RateLimitProperties.Login login = properties.getLogin();
        execute(addScript, loginKey(email, clientIp), login.getWindow(),
                Long.toString(nowMillis()) + "-" + UUID.randomUUID());
    }

    public void clearLoginFailures(String email, String clientIp) {
        if (properties.isEnabled()) {
            redisTemplate.delete(loginKey(email, clientIp));
        }
    }

    public void consumeBookingCreate(UUID customerId) {
        if (!properties.isEnabled()) {
            return;
        }
        RateLimitProperties.Booking booking = properties.getBooking();
        assertAllowed(bookingKey(customerId), booking.getCreateLimit(), booking.getCreateWindow(), true);
    }

    public void consumePublicEndpoint(String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }
        RateLimitProperties.PublicEndpoint publicEndpoint = properties.getPublicEndpoint();
        assertAllowed(publicEndpointKey(clientIp), publicEndpoint.getLimit(), publicEndpoint.getWindow(), true);
    }

    private void assertAllowed(String key, int limit, Duration window, boolean consume) {
        Long retryAfterSeconds;
        if (consume) {
            retryAfterSeconds = execute(consumeScript, key, window, Integer.toString(limit),
                    Long.toString(nowMillis()) + "-" + UUID.randomUUID());
        } else {
            retryAfterSeconds = execute(checkScript, key, window, Integer.toString(limit));
        }
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            throw new RateLimitExceededException(Duration.ofSeconds(retryAfterSeconds));
        }
    }

    private Long execute(DefaultRedisScript<Long> script, String key, Duration window, String... args) {
        String[] redisArgs = new String[2 + args.length];
        redisArgs[0] = Long.toString(nowMillis());
        redisArgs[1] = Long.toString(window.toMillis());
        System.arraycopy(args, 0, redisArgs, 2, args.length);
        return redisTemplate.execute(script, List.of(key), (Object[]) redisArgs);
    }

    private long nowMillis() {
        return clock.millis();
    }

    private String loginKey(String email, String clientIp) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String normalizedIp = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        return "rl:login:" + normalizedEmail + ":" + normalizedIp;
    }

    private String bookingKey(UUID customerId) {
        return "rl:booking:create:" + customerId;
    }

    private String publicEndpointKey(String clientIp) {
        String normalizedIp = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        return "rl:public:" + normalizedIp;
    }
}
