package com.florin.bank.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Short-lived Redis copy of token-hash to customer-id lookups, shared by every instance, so an
 * authenticated request does not need a database round trip. The database stays the source of truth.
 */
@Component
public class SessionCache {

    private static final Logger LOG = LoggerFactory.getLogger(SessionCache.class);
    // Also the longest a revoked session can keep working on an instance that missed the eviction.
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "florin:session:";

    private final StringRedisTemplate redis;

    public SessionCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    Optional<Long> find(String tokenHash) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + tokenHash)).map(Long::valueOf);
        } catch (RuntimeException exception) {
            LOG.warn("Session cache read failed: {}", exception.toString());
            return Optional.empty();
        }
    }

    void store(String tokenHash, Long customerId) {
        try {
            redis.opsForValue().set(PREFIX + tokenHash, customerId.toString(), TTL);
        } catch (RuntimeException exception) {
            LOG.warn("Session cache write failed: {}", exception.toString());
        }
    }

    void evict(String tokenHash) {
        try {
            redis.delete(PREFIX + tokenHash);
        } catch (RuntimeException exception) {
            LOG.warn("Session cache eviction failed: {}", exception.toString());
        }
    }
}
