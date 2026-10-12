package com.florin.bank.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket kept in Redis so the limit holds across every backend instance. The refill and the
 * take happen inside one Lua script, which Redis runs atomically.
 *
 * <p>When Redis is unreachable the limiter falls back to buckets held in this instance's memory.
 * Those are per instance rather than global, so a caller gets N times the limit across N replicas,
 * but login guessing and floods are still slowed instead of being waved through.
 */
@Component
public class RateLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);
    private static final int MAX_LOCAL_BUCKETS = 100_000;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> TAKE = new DefaultRedisScript<>("""
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'at')
            local tokens = tonumber(bucket[1]) or capacity
            local at = tonumber(bucket[2]) or now
            tokens = math.min(capacity, tokens + (now - at) / 1000 * refill)
            local allowed = 0
            local retry = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            else
              retry = math.ceil((1 - tokens) / refill * 1000)
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'at', now)
            redis.call('PEXPIRE', KEYS[1], math.ceil(capacity / refill * 1000))
            return {allowed, math.floor(tokens), retry}
            """, List.class);

    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, LocalBucket> local = new ConcurrentHashMap<>();

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Decision tryAcquire(String key, RateLimitProperties.Rule rule) {
        try {
            List<?> result = redis.execute(TAKE, List.of(key),
                    Integer.toString(rule.capacity()), Double.toString(rule.refillPerSecond()));
            return new Decision(number(result, 0) == 1, number(result, 1), number(result, 2));
        } catch (RuntimeException exception) {
            LOG.warn("Rate limiter falling back to in-memory buckets: {}", exception.toString());
            return tryAcquireLocally(key, rule);
        }
    }

    Decision tryAcquireLocally(String key, RateLimitProperties.Rule rule) {
        if (local.size() >= MAX_LOCAL_BUCKETS && !local.containsKey(key)) {
            // Bound memory under a flood of distinct callers; losing counters is the lesser harm.
            local.clear();
        }
        return local.computeIfAbsent(key, ignored -> new LocalBucket(rule.capacity())).take(rule);
    }

    // Forget buckets that have refilled completely; they would decide exactly as a fresh one.
    @Scheduled(fixedDelay = 60_000)
    void purgeLocalBuckets() {
        local.entrySet().removeIf(entry -> entry.getValue().isFull());
    }

    private static long number(List<?> result, int index) {
        return ((Number) result.get(index)).longValue();
    }

    public record Decision(boolean allowed, long remaining, long retryAfterMillis) {
    }

    private static final class LocalBucket {
        private double tokens;
        private long at = System.currentTimeMillis();
        private int capacity;
        private double refillPerSecond;

        LocalBucket(int capacity) {
            this.tokens = capacity;
            this.capacity = capacity;
        }

        synchronized Decision take(RateLimitProperties.Rule rule) {
            capacity = rule.capacity();
            refillPerSecond = rule.refillPerSecond();
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return new Decision(true, (long) tokens, 0);
            }
            long retry = (long) Math.ceil((1 - tokens) / refillPerSecond * 1000);
            return new Decision(false, 0, retry);
        }

        synchronized boolean isFull() {
            refill();
            return tokens >= capacity;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            tokens = Math.min(capacity, tokens + (now - at) / 1000.0 * refillPerSecond);
            at = now;
        }
    }
}
