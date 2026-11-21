package com.omnibid.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptLimiter {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration WINDOW_DURATION = Duration.ofMinutes(15);
    public static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public boolean isBlocked(String email, String ip) {
        String key = buildKey(email, ip);
        AttemptState state = attempts.get(key);
        if (state == null) {
            return false;
        }
        Instant now = clock.instant();
        if (state.blockedUntil() != null) {
            if (now.isBefore(state.blockedUntil())) {
                return true;
            }
            attempts.remove(key);
            return false;
        }
        return false;
    }

    public void recordFailedAttempt(String email, String ip) {
        String key = buildKey(email, ip);
        Instant now = clock.instant();
        attempts.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.firstAttemptTime().plus(WINDOW_DURATION))) {
                return new AttemptState(1, now, null);
            }
            int newCount = existing.count() + 1;
            Instant blockedUntil = newCount >= MAX_ATTEMPTS ? now.plus(BLOCK_DURATION) : null;
            return new AttemptState(newCount, existing.firstAttemptTime(), blockedUntil);
        });
    }

    public void reset(String email, String ip) {
        String key = buildKey(email, ip);
        attempts.remove(key);
    }

    private String buildKey(String email, String ip) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        String normalizedIp = ip != null ? ip.trim() : "";
        return normalizedEmail + ":" + normalizedIp;
    }

    private record AttemptState(int count, Instant firstAttemptTime, Instant blockedUntil) {}
}
