package com.florin.bank.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimiterFallbackTest {

    @Test
    void limitsStillApplyWhenRedisIsDown() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));
        RateLimiter limiter = new RateLimiter(redis);
        String key = "test:" + UUID.randomUUID();
        var rule = new RateLimitProperties.Rule(3, Duration.ofHours(1));

        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire(key, rule).allowed());
        }
        RateLimiter.Decision refused = limiter.tryAcquire(key, rule);
        assertFalse(refused.allowed());
        assertTrue(refused.retryAfterMillis() > 0);
        assertTrue(limiter.tryAcquire("test:" + UUID.randomUUID(), rule).allowed());
    }
}
