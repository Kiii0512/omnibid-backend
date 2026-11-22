package com.omnibid.security;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {

    private final Clock clock;
    private final AppProperties appProperties;
    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    private record AttemptRecord(int count, Instant windowStart, Instant blockedUntil) {}

    public void checkAllowed(String email, String ip) {
        String key = buildKey(email, ip);
        AttemptRecord record = attempts.get(key);
        if (record == null || record.blockedUntil() == null) {
            return;
        }
        Instant now = clock.instant();
        if (now.isBefore(record.blockedUntil())) {
            long retryAfterSeconds = Math.max(1L, Duration.between(now, record.blockedUntil()).toSeconds());
            throw new BusinessException(
                    ErrorCode.TOO_MANY_REQUESTS,
                    "Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau.",
                    Map.of("retryAfterSeconds", retryAfterSeconds)
            );
        }
        attempts.remove(key, record);
    }

    public void recordFailure(String email, String ip) {
        String key = buildKey(email, ip);
        Instant now = clock.instant();
        AppProperties.Security.Login config = appProperties.getSecurity().getLogin();
        int maxAttempts = config.getMaxAttempts();
        Duration window = config.getWindowMinutes();
        Duration blockDuration = config.getBlockMinutes();

        attempts.compute(key, (k, existing) -> {
            if (existing == null || !now.isBefore(existing.windowStart().plus(window))) {
                int newCount = 1;
                Instant blockedUntil = newCount >= maxAttempts ? now.plus(blockDuration) : null;
                return new AttemptRecord(newCount, now, blockedUntil);
            }
            if (existing.blockedUntil() != null && now.isBefore(existing.blockedUntil())) {
                return existing;
            }
            int newCount = existing.count() + 1;
            Instant blockedUntil = newCount >= maxAttempts ? now.plus(blockDuration) : null;
            return new AttemptRecord(newCount, existing.windowStart(), blockedUntil);
        });
    }

    public void recordSuccess(String email, String ip) {
        attempts.remove(buildKey(email, ip));
    }

    public void clear() {
        attempts.clear();
    }

    private String buildKey(String email, String ip) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String normalizedIp = ip == null ? "" : ip.trim();
        return normalizedEmail + "|" + normalizedIp;
    }
}
