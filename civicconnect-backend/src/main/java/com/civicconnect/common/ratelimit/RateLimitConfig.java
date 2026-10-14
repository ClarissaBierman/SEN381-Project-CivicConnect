package com.civicconnect.common.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;

/**
 * Rate limits (B4). All three are kept in memory, which is enough for one server instance; with several
 * instances the limits would need a shared store (e.g. Redis) - recorded as technical debt.
 *
 *  - apiRequestLimiter:  every authenticated API call, per account (role + id from the JWT)
 *  - registrationLimiter: POST /auth/register, per email address
 *  - loginAttemptLimiter: failed password checks, per email address
 */
@Configuration
@EnableScheduling
public class RateLimitConfig {

    private final Clock clock = Clock.systemUTC();
    private TokenBucketLimiter api;
    private TokenBucketLimiter registration;
    private LoginAttemptLimiter login;

    @Bean
    public TokenBucketLimiter apiRequestLimiter(
            @Value("${civicconnect.rate-limit.api.requests-per-minute:120}") long perMinute) {
        api = new TokenBucketLimiter(perMinute, Duration.ofMinutes(1), clock);
        return api;
    }

    @Bean
    public TokenBucketLimiter registrationLimiter(
            @Value("${civicconnect.rate-limit.register.per-email-per-hour:5}") long perHour) {
        registration = new TokenBucketLimiter(perHour, Duration.ofHours(1), clock);
        return registration;
    }

    @Bean
    public LoginAttemptLimiter loginAttemptLimiter(
            @Value("${civicconnect.rate-limit.login.max-failures:5}") int maxFailures,
            @Value("${civicconnect.rate-limit.login.window-minutes:15}") long windowMinutes) {
        login = new LoginAttemptLimiter(maxFailures, Duration.ofMinutes(windowMinutes), clock);
        return login;
    }

    /** Every 5 minutes, forget keys that are back to full allowance. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void evictIdleKeys() {
        if (api != null) api.evictIdle();
        if (registration != null) registration.evictIdle();
        if (login != null) login.evictIdle();
    }
}
