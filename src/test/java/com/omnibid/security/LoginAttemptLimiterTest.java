package com.omnibid.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptLimiterTest {

    private MutableClock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-21T10:00:00Z"));
        limiter = new LoginAttemptLimiter(clock);
    }

    @Test
    void testBlocksAfterFiveFailedAttempts() {
        String email = "user@example.com";
        String ip = "192.168.1.1";

        assertFalse(limiter.isBlocked(email, ip));

        for (int i = 0; i < 4; i++) {
            limiter.recordFailedAttempt(email, ip);
            assertFalse(limiter.isBlocked(email, ip));
        }

        limiter.recordFailedAttempt(email, ip);
        assertTrue(limiter.isBlocked(email, ip));

        // Advance 16 minutes -> should be unblocked
        clock.advance(Duration.ofMinutes(16));
        assertFalse(limiter.isBlocked(email, ip));
    }

    @Test
    void testResetClearsAttempts() {
        String email = "user@example.com";
        String ip = "192.168.1.1";

        for (int i = 0; i < 5; i++) {
            limiter.recordFailedAttempt(email, ip);
        }
        assertTrue(limiter.isBlocked(email, ip));

        limiter.reset(email, ip);
        assertFalse(limiter.isBlocked(email, ip));
    }

    private static class MutableClock extends Clock {
        private Instant currentInstant;

        MutableClock(Instant initialInstant) {
            this.currentInstant = initialInstant;
        }

        void advance(Duration duration) {
            this.currentInstant = this.currentInstant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
