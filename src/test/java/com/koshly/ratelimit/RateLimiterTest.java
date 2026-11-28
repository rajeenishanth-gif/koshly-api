package com.koshly.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** The token bucket on its own, with a clock moved by hand. */
class RateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final RateLimiter limiter = new RateLimiter(60, now::get); // one token per second

    @Test
    void allowsABurstUpToTheLimitThenAsksToWait() {
        for (int i = 0; i < 60; i++) {
            assertThat(limiter.tryAcquire("alice")).isZero();
        }
        assertThat(limiter.tryAcquire("alice")).isEqualTo(1); // next token in a second
    }

    @Test
    void refillsOverTime() {
        for (int i = 0; i < 60; i++) limiter.tryAcquire("alice");
        assertThat(limiter.tryAcquire("alice")).isPositive();

        now.addAndGet(5_000); // 5 seconds = 5 tokens
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("alice")).isZero();
        }
        assertThat(limiter.tryAcquire("alice")).isPositive();
    }

    @Test
    void neverRefillsAboveTheLimit() {
        now.addAndGet(60 * 60_000); // an hour idle
        for (int i = 0; i < 60; i++) limiter.tryAcquire("alice");
        assertThat(limiter.tryAcquire("alice")).isPositive();
    }

    @Test
    void eachPersonHasTheirOwnBucket() {
        for (int i = 0; i < 60; i++) limiter.tryAcquire("alice");
        assertThat(limiter.tryAcquire("alice")).isPositive();
        assertThat(limiter.tryAcquire("bob")).isZero();
    }

    @Test
    void forgetsIdleBuckets() {
        limiter.tryAcquire("alice");
        now.addAndGet(11 * 60_000); // longer than the 10 idle minutes
        for (int i = 0; i < 10_000; i++) limiter.tryAcquire("bob"); // the sweep runs every 10,000 requests
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }
}
