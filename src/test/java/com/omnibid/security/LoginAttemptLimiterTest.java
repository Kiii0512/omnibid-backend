package com.omnibid.security;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.config.AppProperties;
import com.omnibid.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptLimiterTest {

    private MutableClock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-22T02:00:00Z"));
        AppProperties props = new AppProperties();
        limiter = new LoginAttemptLimiter(clock, props);
    }

    @Test
    void blocksAfterFiveFailuresAndUnblocksAfterBlockDuration() {
        for (int i = 0; i < 4; i++) {
            limiter.checkAllowed("User@Example.com", "10.0.0.1");
            limiter.recordFailure("user@example.com", "10.0.0.1");
        }
        assertDoesNotThrow(() -> limiter.checkAllowed("user@example.com", "10.0.0.1"));

        limiter.recordFailure("user@example.com", "10.0.0.1");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> limiter.checkAllowed("USER@example.com", "10.0.0.1")
        );
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getCode());
        assertNotNull(ex.getDetails());
        assertEquals(900L, ex.getDetails().get("retryAfterSeconds"));

        // Different IP is unaffected
        assertDoesNotThrow(() -> limiter.checkAllowed("user@example.com", "10.0.0.2"));

        // Advance past 15-minute block
        clock.advance(Duration.ofMinutes(15));
        assertDoesNotThrow(() -> limiter.checkAllowed("user@example.com", "10.0.0.1"));
    }

    @Test
    void recordSuccessResetsCounter() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("user@example.com", "10.0.0.1");
        }
        limiter.recordSuccess("user@example.com", "10.0.0.1");
        limiter.recordFailure("user@example.com", "10.0.0.1");
        assertDoesNotThrow(() -> limiter.checkAllowed("user@example.com", "10.0.0.1"));
    }

    @Test
    void tenThousandRandomEmailsDoNotExceedMaxEntries() {
        AppProperties boundedProps = new AppProperties();
        boundedProps.getSecurity().getLogin().setMaxEntries(500);
        LoginAttemptLimiter boundedLimiter = new LoginAttemptLimiter(clock, boundedProps);

        for (int i = 0; i < 10_000; i++) {
            boundedLimiter.recordFailure("random" + i + "@attacker.test", "198.51.100.1");
        }

        assertTrue(boundedLimiter.size() <= 500, "Cache size must not exceed maxEntries");
    }

    @Test
    void expiredEntriesAreEvictedWhenClockAdvances() {
        for (int i = 0; i < 20; i++) {
            limiter.recordFailure("user" + i + "@example.com", "10.0.0.1");
        }
        assertEquals(20, limiter.size());

        clock.advance(Duration.ofMinutes(16));
        limiter.evictExpired();

        assertEquals(0, limiter.size());
    }
}
