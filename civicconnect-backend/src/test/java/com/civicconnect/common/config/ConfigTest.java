package com.civicconnect.common.config;

import com.civicconnect.common.ratelimit.RateLimitConfig;
import com.civicconnect.common.ratelimit.TokenBucketLimiter;
import com.civicconnect.common.time.Time;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigTest {

    @AfterEach
    void systemClock() {
        Time.useClock(null);
    }

    @Test
    @DisplayName("CORS allows the Angular origin on /api and exposes Retry-After")
    void cors() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/categories");
        CorsConfiguration cors = new WebConfig().corsConfigurationSource(List.of("http://localhost:4200"))
                .getCorsConfiguration(request);

        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:4200");
        assertThat(cors.getExposedHeaders()).contains("Retry-After");
        assertThat(cors.getAllowedMethods()).doesNotContain("DELETE");
    }

    @Test
    @DisplayName("Rate limit beans use the configured sizes and idle keys can be evicted at any time")
    void rateLimitBeans() {
        RateLimitConfig config = new RateLimitConfig();
        assertThatCode(config::evictIdleKeys).doesNotThrowAnyException();
        TokenBucketLimiter api = config.apiRequestLimiter(2);
        config.ipRequestLimiter(5);
        config.authIpLimiter(5);
        config.registrationLimiter(5);
        config.loginAttemptLimiter(5, 15).recordFailure("a@b.c");

        api.tryConsume("k");
        api.tryConsume("k");
        assertThat(api.tryConsume("k")).isPositive();
        assertThatCode(config::evictIdleKeys).doesNotThrowAnyException();
        assertThat(new SecurityConfig().passwordEncoder().matches("x", new SecurityConfig().passwordEncoder().encode("x"))).isTrue();
    }

    @Test
    @DisplayName("A bucket must have a positive size and period")
    void invalidBucket() {
        assertThatThrownBy(() -> new TokenBucketLimiter(0, Duration.ofMinutes(1), Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketLimiter(1, Duration.ZERO, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Time is UTC and can be frozen for tests")
    void time() {
        Time.useClock(Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), ZoneOffset.ofHours(2)));
        assertThat(Time.now().toString()).isEqualTo("2026-12-31T23:30");
        assertThat(Time.currentYear().getValue()).isEqualTo(2026);
        assertThat(Time.nowInstant()).isEqualTo(Instant.parse("2026-12-31T23:30:00Z"));
        assertThat(Time.toInstant(null)).isNull();
        assertThat(Time.toInstant(Time.now())).isEqualTo(Instant.parse("2026-12-31T23:30:00Z"));
    }
}
