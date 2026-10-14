package com.civicconnect.common.ratelimit;

import com.civicconnect.common.error.ErrorCode;
import com.civicconnect.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B4: failed sign-ins are counted per email address. Boundary values around the limit of 5 failures
 * and the 15-minute window (BVA).
 */
class LoginAttemptLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(5, Duration.ofMinutes(15), clock);

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) limiter.recordFailure(email);
    }

    @Test
    @DisplayName("4 failures: still allowed; 5 failures: locked with 429 and a Retry-After of 15 minutes")
    void locksOnFifthFailure() {
        fail("thabo@example.com", 4);
        assertThatCode(() -> limiter.checkAllowed("thabo@example.com")).doesNotThrowAnyException();

        fail("thabo@example.com", 1);
        assertThatThrownBy(() -> limiter.checkAllowed("thabo@example.com"))
                .isInstanceOf(RateLimitedException.class)
                .satisfies(e -> {
                    RateLimitedException r = (RateLimitedException) e;
                    assertThat(r.getCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                    assertThat(r.getRetryAfterSeconds()).isEqualTo(15 * 60);
                });
    }

    @Test
    @DisplayName("The count is per email, whatever device or network: case and spaces do not make a new key")
    void keyIsTheEmailAddress() {
        fail("Thabo@Example.com", 3);
        fail("  thabo@example.com ", 2);

        assertThatThrownBy(() -> limiter.checkAllowed("THABO@EXAMPLE.COM")).isInstanceOf(RateLimitedException.class);
        assertThatCode(() -> limiter.checkAllowed("naledi@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Unlocks one second after the oldest counted failure leaves the 15-minute window, not before")
    void unlocksWhenWindowPasses() {
        fail("a@example.com", 5);

        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com")).isInstanceOf(RateLimitedException.class);

        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> limiter.checkAllowed("a@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A successful sign-in clears the count")
    void successResets() {
        fail("a@example.com", 4);
        limiter.recordSuccess("a@example.com");
        fail("a@example.com", 4);

        assertThatCode(() -> limiter.checkAllowed("a@example.com")).doesNotThrowAnyException();
    }
}
