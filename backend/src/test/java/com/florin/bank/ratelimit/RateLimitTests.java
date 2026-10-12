package com.florin.bank.ratelimit;

import com.florin.bank.TestBank;
import com.florin.bank.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"florin.rate-limit.write.capacity=3", "florin.rate-limit.money.capacity=2"})
@AutoConfigureMockMvc
class RateLimitTests {

    @Autowired RateLimiter limiter;
    @Autowired MockMvc mvc;

    @Test
    void aBucketAllowsItsBurstThenRefusesUntilItRefills() {
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

    @Test
    void theApiAnswers429WithRetryAfterOnceTheBucketIsEmpty() throws Exception {
        // Anonymous writes are refused by security, but only after they have spent a token.
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/accounts").with(request -> { request.setRemoteAddr("203.0.113.9"); return request; })
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/accounts").with(request -> { request.setRemoteAddr("203.0.113.9"); return request; })
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void moneyMovementHasItsOwnTighterBucketPerCustomer() throws Exception {
        TestBank bank = new TestBank(mvc);
        TestBank.Customer asha = bank.register("Asha Rao");
        bank.deposit(asha, asha.accountId(), "10", null).andExpect(status().isCreated());
        bank.deposit(asha, asha.accountId(), "10", null).andExpect(status().isCreated());
        bank.deposit(asha, asha.accountId(), "10", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "2"));

        TestBank.Customer ravi = bank.register("Ravi Kumar");
        bank.deposit(ravi, ravi.accountId(), "10", null).andExpect(status().isCreated());
    }
}
