package com.omnibid.security;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class LoginAttemptLimiter {

    private static final int CLEANUP_STRIDE = 128;

    private final Clock clock;
    private final AppProperties appProperties;
    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<String, AttemptRecord> attempts;
    private int writeOps = 0;

    private record AttemptRecord(int count, Instant windowStart, Instant blockedUntil, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    public LoginAttemptLimiter(Clock clock, AppProperties appProperties) {
        this.clock = clock;
        this.appProperties = appProperties;
        this.attempts = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, AttemptRecord> eldest) {
                return size() > Math.max(1, appProperties.getSecurity().getLogin().getMaxEntries());
            }
        };
    }

    public void checkAllowed(String email, String ip) {
        String key = buildKey(email, ip);
        Instant now = clock.instant();
        AttemptRecord record;

        lock.lock();
        try {
            record = attempts.get(key);
            if (record == null) {
                return;
            }
            if (record.isExpired(now)) {
                attempts.remove(key);
                return;
            }
            if (record.blockedUntil() != null && !now.isBefore(record.blockedUntil())) {
                attempts.remove(key);
                return;
            }
        } finally {
            lock.unlock();
        }

        if (record.blockedUntil() != null && now.isBefore(record.blockedUntil())) {
            long retryAfterSeconds = Math.max(1L, Duration.between(now, record.blockedUntil()).toSeconds());
            throw new BusinessException(
                    ErrorCode.TOO_MANY_REQUESTS,
                    "Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau.",
                    Map.of("retryAfterSeconds", retryAfterSeconds)
            );
        }
    }

    public void recordFailure(String email, String ip) {
        String key = buildKey(email, ip);
        Instant now = clock.instant();
        AppProperties.Security.Login config = appProperties.getSecurity().getLogin();
        int maxAttempts = config.getMaxAttempts();
        Duration window = config.getWindowMinutes();
        Duration blockDuration = config.getBlockMinutes();
        int maxEntries = Math.max(1, config.getMaxEntries());

        lock.lock();
        try {
            if (++writeOps >= CLEANUP_STRIDE || attempts.size() >= maxEntries) {
                writeOps = 0;
                evictExpiredLocked(now);
            }

            AttemptRecord existing = attempts.get(key);
            AttemptRecord updated;
            if (existing == null || existing.isExpired(now) || !now.isBefore(existing.windowStart().plus(window))) {
                int newCount = 1;
                Instant blockedUntil = newCount >= maxAttempts ? now.plus(blockDuration) : null;
                Instant expiresAt = blockedUntil != null ? blockedUntil : now.plus(window);
                updated = new AttemptRecord(newCount, now, blockedUntil, expiresAt);
            } else if (existing.blockedUntil() != null && now.isBefore(existing.blockedUntil())) {
                updated = existing;
            } else {
                int newCount = existing.count() + 1;
                Instant blockedUntil = newCount >= maxAttempts ? now.plus(blockDuration) : null;
                Instant windowExpiry = existing.windowStart().plus(window);
                Instant expiresAt = (blockedUntil != null && blockedUntil.isAfter(windowExpiry))
                        ? blockedUntil
                        : windowExpiry;
                updated = new AttemptRecord(newCount, existing.windowStart(), blockedUntil, expiresAt);
            }

            attempts.put(key, updated);
        } finally {
            lock.unlock();
        }
    }

    public void recordSuccess(String email, String ip) {
        String key = buildKey(email, ip);
        lock.lock();
        try {
            attempts.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            attempts.clear();
            writeOps = 0;
        } finally {
            lock.unlock();
        }
    }

    int size() {
        lock.lock();
        try {
            return attempts.size();
        } finally {
            lock.unlock();
        }
    }

    void evictExpired() {
        Instant now = clock.instant();
        lock.lock();
        try {
            evictExpiredLocked(now);
        } finally {
            lock.unlock();
        }
    }

    private void evictExpiredLocked(Instant now) {
        Iterator<Map.Entry<String, AttemptRecord>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired(now)) {
                it.remove();
            }
        }
    }

    private String buildKey(String email, String ip) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String normalizedIp = ip == null ? "" : ip.trim();
        return normalizedEmail + "|" + normalizedIp;
    }
}
