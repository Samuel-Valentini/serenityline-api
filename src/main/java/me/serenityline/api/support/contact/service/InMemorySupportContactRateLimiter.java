package me.serenityline.api.support.contact.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class InMemorySupportContactRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_REQUESTS_PER_WINDOW = 5;
    private static final long MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final Cache<String, RateLimitBucket> buckets;

    public InMemorySupportContactRateLimiter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterWrite(WINDOW)
                .build();
    }

    public void check(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        String normalizedKey = key.trim();
        Instant now = Instant.now(clock);

        RateLimitBucket bucket = buckets.asMap().compute(
                normalizedKey,
                (ignored, current) -> nextBucket(current, now)
        );

        if (bucket.count() > MAX_REQUESTS_PER_WINDOW) {
            throw new TooManySupportContactRequestsException();
        }
    }

    private RateLimitBucket nextBucket(RateLimitBucket current, Instant now) {
        if (current == null || !now.isBefore(current.windowStartedAt().plus(WINDOW))) {
            return new RateLimitBucket(now, 1);
        }

        return new RateLimitBucket(current.windowStartedAt(), current.count() + 1);
    }

    private record RateLimitBucket(Instant windowStartedAt, int count) {
    }
}