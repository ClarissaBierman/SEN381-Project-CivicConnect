package com.civicconnect.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneOffset;

/**
 * The one place the application reads the clock. Every timestamp is UTC: the database columns are
 * TIMESTAMP (no time zone) and hold UTC, the database session runs in UTC (connection-init-sql), and
 * the API sends Instants ("...Z") so the browser shows them in the user's own time zone.
 */
public final class Time {

    private static volatile Clock clock = Clock.systemUTC();

    private Time() {}

    /** Current UTC date-time, for entity timestamp columns. */
    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public static Instant nowInstant() {
        return clock.instant();
    }

    public static Year currentYear() {
        return Year.now(clock);
    }

    /** A stored UTC date-time as an Instant for the API (null stays null). */
    public static Instant toInstant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }

    /** Tests only: freeze or move the clock. Pass null to go back to the system clock. */
    public static void useClock(Clock testClock) {
        clock = testClock == null ? Clock.systemUTC() : testClock.withZone(ZoneOffset.UTC);
    }
}
