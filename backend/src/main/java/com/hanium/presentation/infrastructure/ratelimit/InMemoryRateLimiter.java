package com.hanium.presentation.infrastructure.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();
    private final Clock clock;

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Decision acquire(String key, int limit, Duration duration) {
        Instant now = clock.instant();
        Window updated = windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.expiresAt())) {
                return new Window(1, now.plus(duration));
            }
            return new Window(current.count() + 1, current.expiresAt());
        });
        if (requestCounter.incrementAndGet() % 1_000 == 0) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        }
        long retryAfter = Math.max(1, Duration.between(now, updated.expiresAt()).toSeconds() + 1);
        return new Decision(updated.count() <= limit, retryAfter);
    }

    record Window(int count, Instant expiresAt) {
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }
}
