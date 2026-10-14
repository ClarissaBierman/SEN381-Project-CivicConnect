package com.civicconnect.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts failed password checks per email address (B4). After {@code maxFailures} failures inside
 * {@code window}, the address is locked until the oldest failure is older than the window - even for
 * the correct password, otherwise an attacker would simply keep guessing.
 *
 * Keyed by email, not by IP address, so changing device, network or VPN does not reset the count.
 * Trade-off: someone who knows a user's email can keep that account locked by failing on purpose; the
 * lock only lasts for the window and the real user can still use any session that is already open.
 */
public class LoginAttemptLimiter {

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** Throws 429 if this email is currently locked. Call before checking the password. */
    public void checkAllowed(String email) {
        long retryAfter = secondsUntilUnlocked(key(email));
        if (retryAfter > 0) {
            long minutes = (retryAfter + 59) / 60;
            throw new RateLimitedException("Too many failed sign-in attempts for this email address. "
                    + "Try again in " + minutes + (minutes == 1 ? " minute." : " minutes."), retryAfter);
        }
    }

    public void recordFailure(String email) {
        long now = clock.millis();
        Deque<Long> list = failures.computeIfAbsent(key(email), k -> new ArrayDeque<>());
        synchronized (list) {
            prune(list, now);
            list.addLast(now);
        }
    }

    public void recordSuccess(String email) {
        failures.remove(key(email));
    }

    public void evictIdle() {
        long now = clock.millis();
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }

    private long secondsUntilUnlocked(String key) {
        Deque<Long> list = failures.get(key);
        if (list == null) return 0;
        long now = clock.millis();
        synchronized (list) {
            prune(list, now);
            if (list.size() < maxFailures) return 0;
            // Unlocks when enough old failures have left the window
            long unlockAt = list.stream().skip(list.size() - maxFailures).findFirst().orElse(now) + window.toMillis();
            return Math.max(1, (unlockAt - now + 999) / 1000);
        }
    }

    private void prune(Deque<Long> list, long now) {
        long oldestAllowed = now - window.toMillis();
        while (!list.isEmpty() && list.peekFirst() <= oldestAllowed) {
            list.removeFirst();
        }
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
