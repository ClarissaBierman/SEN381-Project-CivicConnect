package com.civicconnect.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token bucket per key. Each key may make {@code capacity} calls at once and then gets
 * {@code capacity} new calls spread evenly over {@code refillPeriod}.
 * Example: capacity 120, period 1 minute = bursts of up to 120 calls, then 2 calls per second.
 *
 * In memory: the counts are per server instance and reset on restart (see RateLimitConfig).
 */
public class TokenBucketLimiter {

    private final long capacity;
    private final double tokensPerMilli;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketLimiter(long capacity, Duration refillPeriod, Clock clock) {
        if (capacity < 1 || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("capacity and refillPeriod must be positive");
        }
        this.capacity = capacity;
        this.tokensPerMilli = (double) capacity / refillPeriod.toMillis();
        this.clock = clock;
    }

    /**
     * Takes one token for the key.
     * @return 0 if the call is allowed, otherwise the number of seconds until a token is available
     */
    public long tryConsume(String key) {
        long now = clock.millis();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        synchronized (bucket) {
            bucket.refill(now, capacity, tokensPerMilli);
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return 0;
            }
            double missing = 1 - bucket.tokens;
            return (long) Math.ceil(missing / tokensPerMilli / 1000.0);
        }
    }

    /** Forgets buckets that have refilled completely, so the map does not grow forever. */
    public void evictIdle() {
        long now = clock.millis();
        buckets.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                e.getValue().refill(now, capacity, tokensPerMilli);
                return e.getValue().tokens >= capacity;
            }
        });
    }

    int trackedKeys() { return buckets.size(); }

    private static final class Bucket {
        private double tokens;
        private long lastRefill;

        Bucket(long capacity, long now) {
            this.tokens = capacity;
            this.lastRefill = now;
        }

        void refill(long now, long capacity, double tokensPerMilli) {
            if (now > lastRefill) {
                tokens = Math.min(capacity, tokens + (now - lastRefill) * tokensPerMilli);
                lastRefill = now;
            }
        }
    }
}
