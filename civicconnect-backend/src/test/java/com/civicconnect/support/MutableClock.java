package com.civicconnect.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the test moves by hand, for time-based rules (rate limits, overdue). */
public class MutableClock extends Clock {

    private Instant now;

    public MutableClock() { this(Instant.parse("2026-10-14T10:00:00Z")); }

    public MutableClock(Instant start) { this.now = start; }

    public void advance(Duration d) { now = now.plus(d); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override public Clock withZone(ZoneId zone) { return this; }

    @Override public Instant instant() { return now; }
}
