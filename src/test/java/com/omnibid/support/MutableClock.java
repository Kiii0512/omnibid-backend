package com.omnibid.support;

import com.omnibid.config.DateTimeConfig;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public class MutableClock extends Clock {

    private Instant currentInstant;
    private final ZoneId zone;

    public MutableClock(Instant initialInstant, ZoneId zone) {
        this.currentInstant = initialInstant;
        this.zone = zone;
    }

    public MutableClock() {
        this(Instant.now(), DateTimeConfig.DEFAULT_ZONE);
    }

    public synchronized void set(Instant instant) {
        this.currentInstant = instant;
    }

    public synchronized void advance(Duration duration) {
        this.currentInstant = this.currentInstant.plus(duration);
    }

    public synchronized void reset() {
        this.currentInstant = Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(this.currentInstant, zone);
    }

    @Override
    public synchronized Instant instant() {
        return currentInstant;
    }
}
