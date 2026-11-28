package com.koshly.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * A "token bucket" per person: each bucket holds up to {@code perMinute} tokens and
 * refills steadily (perMinute tokens over 60 seconds). Every request takes one token;
 * an empty bucket means "too many requests, try again in a few seconds".
 *
 * Why a token bucket: it allows a short burst (opening the app loads several things at
 * once) but caps the steady rate, and it needs only two numbers per person.
 *
 * Counts are kept in this server's memory, so each Cloud Run instance counts on its own
 * (at most 2 instances, so a person gets at most twice the limit). Good enough to stop
 * a bot hammering the API; a shared store (Redis) would make it exact if ever needed.
 */
public class RateLimiter {

    private static final long MINUTE_MS = 60_000;
    /** Forget buckets unused for this long; a full bucket holds no information. */
    private static final long IDLE_MS = 10 * MINUTE_MS;
    /** Look for idle buckets once every this many requests. */
    private static final long SWEEP_EVERY = 10_000;

    private final int perMinute;
    private final LongSupplier nowMs;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();

    public RateLimiter(int perMinute) {
        this(perMinute, System::currentTimeMillis);
    }

    /** For tests: a clock that can be moved by hand. */
    RateLimiter(int perMinute, LongSupplier nowMs) {
        if (perMinute < 1) throw new IllegalArgumentException("perMinute must be at least 1");
        this.perMinute = perMinute;
        this.nowMs = nowMs;
    }

    /**
     * Takes a token for {@code key}.
     *
     * @return 0 if the request may go ahead, otherwise how many whole seconds until it may
     */
    public long tryAcquire(String key) {
        long now = nowMs.getAsLong();
        if (requests.incrementAndGet() % SWEEP_EVERY == 0) sweep(now);
        return buckets.computeIfAbsent(key, k -> new Bucket(perMinute, now)).take(now, perMinute);
    }

    int trackedKeys() {
        return buckets.size();
    }

    private void sweep(long now) {
        buckets.values().removeIf(bucket -> bucket.idleSince(now) > IDLE_MS);
    }

    private static final class Bucket {
        private double tokens;
        private long updatedMs;

        Bucket(int perMinute, long now) {
            this.tokens = perMinute;
            this.updatedMs = now;
        }

        synchronized long take(long now, int perMinute) {
            double perMs = perMinute / (double) MINUTE_MS;
            tokens = Math.min(perMinute, tokens + Math.max(0, now - updatedMs) * perMs);
            updatedMs = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return Math.max(1, (long) Math.ceil((1 - tokens) / perMs / 1000));
        }

        synchronized long idleSince(long now) {
            return now - updatedMs;
        }
    }
}
