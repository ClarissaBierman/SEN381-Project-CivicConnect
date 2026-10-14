package com.civicconnect.common.ratelimit;

import com.civicconnect.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** B4: the per-account API limit - a burst up to the capacity, then an even refill. */
class TokenBucketLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final TokenBucketLimiter limiter = new TokenBucketLimiter(120, Duration.ofMinutes(1), clock);

    @Test
    @DisplayName("120 calls at once are allowed, the 121st is refused with a 1 second wait")
    void burstThenRefuse() {
        for (int i = 0; i < 120; i++) {
            assertThat(limiter.tryConsume("STAFF:3")).as("call %d", i + 1).isZero();
        }
        assertThat(limiter.tryConsume("STAFF:3")).isEqualTo(1);
    }

    @Test
    @DisplayName("Tokens come back at 2 per second, and accounts do not share a bucket")
    void refillsAndIsPerAccount() {
        for (int i = 0; i < 120; i++) limiter.tryConsume("STAFF:3");

        assertThat(limiter.tryConsume("MANAGEMENT:1")).as("another account is unaffected").isZero();
        clock.advance(Duration.ofMillis(500));
        assertThat(limiter.tryConsume("STAFF:3")).as("one token after half a second").isZero();
        assertThat(limiter.tryConsume("STAFF:3")).isPositive();
    }

    @Test
    @DisplayName("Full, idle buckets are forgotten so memory does not grow")
    void evictsIdleBuckets() {
        limiter.tryConsume("REQUESTER:1");
        clock.advance(Duration.ofMinutes(1));
        limiter.evictIdle();
        assertThat(limiter.trackedKeys()).isZero();
    }
}
