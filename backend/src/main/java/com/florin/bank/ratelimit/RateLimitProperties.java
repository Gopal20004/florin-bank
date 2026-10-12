package com.florin.bank.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Each rule is a token bucket: {@code capacity} requests of burst, refilled evenly over {@code per}. */
@ConfigurationProperties("florin.rate-limit")
public record RateLimitProperties(boolean enabled, Rule auth, Rule money, Rule lookup, Rule write, Rule read) {

    public record Rule(int capacity, Duration per) {

        double refillPerSecond() {
            return capacity * 1000.0 / per.toMillis();
        }
    }
}
