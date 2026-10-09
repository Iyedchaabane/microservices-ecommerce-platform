package com.ichaabane.gateway.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token-bucket arithmetic: burst capacity, refill timing, per-key isolation,
 * eviction and thread safety. Deterministic via an injected nanosecond clock —
 * no janitor thread and no wall-clock sleeps.
 */
class TokenBucketRateLimiterTest {

    private final AtomicLong now = new AtomicLong(0);

    private TokenBucketRateLimiter limiter(int capacity, int refill, int intervalSec) {
        return new TokenBucketRateLimiter(capacity, refill, Duration.ofSeconds(intervalSec),
                Duration.ofMinutes(10), now::get);
    }

    @Test
    @DisplayName("Allows the full burst, then rejects with 0")
    void burstThenReject() {
        TokenBucketRateLimiter limiter = limiter(3, 1, 10);

        assertThat(limiter.tryAcquire("ip1")).isEqualTo(3);
        assertThat(limiter.tryAcquire("ip1")).isEqualTo(2);
        assertThat(limiter.tryAcquire("ip1")).isEqualTo(1);
        assertThat(limiter.tryAcquire("ip1")).isZero();
    }

    @Test
    @DisplayName("Refills tokens after the interval elapses")
    void refillAfterInterval() {
        TokenBucketRateLimiter limiter = limiter(2, 1, 10);

        assertThat(limiter.tryAcquire("ip1")).isEqualTo(2);
        assertThat(limiter.tryAcquire("ip1")).isEqualTo(1);
        assertThat(limiter.tryAcquire("ip1")).isZero();

        now.addAndGet(10_000_000_000L); // +10s → +1 token

        assertThat(limiter.tryAcquire("ip1")).isEqualTo(1);
        assertThat(limiter.tryAcquire("ip1")).isZero();
    }

    @Test
    @DisplayName("Partial intervals grant nothing (no fractional tokens)")
    void partialIntervalGrantsNothing() {
        TokenBucketRateLimiter limiter = limiter(1, 1, 10);

        assertThat(limiter.tryAcquire("ip1")).isEqualTo(1);
        assertThat(limiter.tryAcquire("ip1")).isZero();

        now.addAndGet(9_999_999_999L); // just under one interval
        assertThat(limiter.retryAfterSeconds("ip1")).isEqualTo(1);

        now.addAndGet(10_000_000_000L); // one more full interval
        assertThat(limiter.tryAcquire("ip1")).isEqualTo(1);
    }

    @Test
    @DisplayName("Rejected requests never consume or corrupt remaining capacity")
    void rejectedRequestsDoNotConsumeTokens() {
        TokenBucketRateLimiter limiter = limiter(2, 1, 10);

        assertThat(limiter.tryAcquire("ip1")).isEqualTo(2);
        assertThat(limiter.tryAcquire("ip1")).isEqualTo(1);
        // 50 further attempts while empty must all be rejected...
        for (int i = 0; i < 50; i++) {
            assertThat(limiter.tryAcquire("ip1")).isZero();
        }
        // ...and must not have pushed the refill base forward: one interval later
        // exactly one token is available (not zero, not more).
        now.addAndGet(10_000_000_000L);
        assertThat(limiter.tryAcquire("ip1")).isEqualTo(1);
        assertThat(limiter.tryAcquire("ip1")).isZero();
    }

    @Test
    @DisplayName("Clients are isolated: one exhausted bucket never affects another")
    void clientsAreIsolated() {
        TokenBucketRateLimiter limiter = limiter(1, 1, 10);

        assertThat(limiter.tryAcquire("ip1")).isEqualTo(1);
        assertThat(limiter.tryAcquire("ip1")).isZero();
        // ip2 still gets its own full bucket (availability = 1 after its request)
        assertThat(limiter.tryAcquire("ip2")).isEqualTo(1);
        assertThat(limiter.trackedClientCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("retryAfterSeconds equals the wait for the next token; 0 after refill")
    void retryAfterSemantics() {
        TokenBucketRateLimiter limiter = limiter(1, 1, 10);

        limiter.tryAcquire("ip1"); // bucket empty; next token at t=10s
        assertThat(limiter.retryAfterSeconds("ip1")).isEqualTo(10);

        now.addAndGet(10_000_000_000L);
        assertThat(limiter.retryAfterSeconds("ip1")).isZero();
    }

    @Test
    @DisplayName("Retry-After accounts for partially elapsed intervals")
    void retryAfterSpansPartiallyElapsedInterval() {
        TokenBucketRateLimiter limiter = limiter(1, 1, 5);

        assertThat(limiter.tryAcquire("ip1")).isEqualTo(1);
        now.addAndGet(3_000_000_000L); // t=3s; next token lands at t=5s

        long retry = limiter.retryAfterSeconds("ip1");
        assertThat(retry).isEqualTo(2); // ceil(5s-3s)
    }

    @Test
    @DisplayName("Retry-After never returns 0 or a negative value for an empty bucket")
    void retryAfterIsPositiveWhenLimited() {
        TokenBucketRateLimiter limiter = limiter(1, 1, 2);

        limiter.tryAcquire("ip1"); // empty
        now.addAndGet(1_999_999_999L); // effectively the last nanosecond of the window
        assertThat(limiter.retryAfterSeconds("ip1")).isGreaterThanOrEqualTo(1);

        // An unknown client is never limited.
        assertThat(limiter.retryAfterSeconds("never-seen")).isZero();
    }

    @Test
    @DisplayName("Idle buckets are evicted, recently used ones are kept")
    void evictsIdleEntries() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(
                2, 1, Duration.ofSeconds(1), Duration.ofSeconds(10), now::get);

        for (int i = 0; i < 1000; i++) {
            limiter.tryAcquire("ip-" + i);
        }
        assertThat(limiter.trackedClientCount()).isEqualTo(1000);

        // Half the window: the deterministic sweep keeps everyone.
        now.addAndGet(5_000_000_000L);
        limiter.evictIdleEntries(now.get());
        assertThat(limiter.trackedClientCount()).isEqualTo(1000);

        // Past the window: idle buckets go, a freshly used one stays.
        now.addAndGet(6_000_000_000L); // t=11s
        limiter.tryAcquire("active-ip"); // lastAccess = 11s
        limiter.evictIdleEntries(now.get());
        assertThat(limiter.trackedClientCount()).isEqualTo(1);
        // The survivor is the active client, still serviceable (one token was
        // consumed by the request that kept it alive).
        assertThat(limiter.tryAcquire("active-ip")).isEqualTo(1);
    }

    @Test
    @DisplayName("High-cardinality traffic is reclaimed after the idle window")
    void highCardinalityIsBounded() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(
                1, 1, Duration.ofSeconds(1), Duration.ofSeconds(30), now::get);

        for (int i = 0; i < 50_000; i++) {
            limiter.tryAcquire("client-" + i);
        }
        assertThat(limiter.trackedClientCount()).isEqualTo(50_000);

        now.addAndGet(31_000_000_000L); // past the eviction window
        limiter.evictIdleEntries(now.get());
        assertThat(limiter.trackedClientCount()).isZero();
    }

    @Test
    @DisplayName("Concurrent requests cannot exceed the configured capacity")
    void concurrentRequestsRespectCapacity() throws Exception {
        int capacity = 100;
        TokenBucketRateLimiter limiter = limiter(capacity, 1, 3600); // no refill during the test

        int threads = 16;
        int attemptsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (limiter.tryAcquire("shared-ip") > 0) {
                            allowed.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(allowed.get()).isEqualTo(capacity);
    }

    @Test
    @DisplayName("Concurrent requests on distinct clients each get a full bucket")
    void concurrentDistinctClientsAreIndependent() throws Exception {
        int capacity = 20;
        TokenBucketRateLimiter limiter = limiter(capacity, 1, 3600);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                String key = "client-" + t;
                futures.add(pool.submit(() -> {
                    start.await();
                    int accepted = 0;
                    for (int i = 0; i < 100; i++) {
                        if (limiter.tryAcquire(key) > 0) {
                            accepted++;
                        }
                    }
                    return accepted;
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                assertThat(future.get()).isEqualTo(capacity);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Capacity is respected after long idle (saturation, not accumulation)")
    void noAccumulationPastCapacity() {
        TokenBucketRateLimiter limiter = limiter(2, 1, 1);

        limiter.tryAcquire("ip1");
        now.addAndGet(60_000_000_000L); // 60 one-second intervals → still capped at capacity
        assertThat(limiter.tryAcquire("ip1")).isEqualTo(2);
        assertThat(limiter.tryAcquire("ip1")).isEqualTo(1);
        assertThat(limiter.tryAcquire("ip1")).isZero();
    }

    @Test
    @DisplayName("Invalid configuration is rejected instead of disabling protection")
    void rejectsInvalidConfig() {
        assertThatThrownBy(() -> limiter(0, 1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter(1, 0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter(1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketRateLimiter(
                1, 1, Duration.ofSeconds(10), Duration.ZERO, now::get))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketRateLimiter(
                1, 1, Duration.ZERO, Duration.ofSeconds(10), now::get))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketRateLimiter(
                1, 1, Duration.ofSeconds(10), Duration.ofSeconds(10), 0, now::get))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("maxBuckets limit is strictly enforced under high-cardinality traffic")
    void maxBucketsIsStrictlyEnforced() {
        int maxBuckets = 10;
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(
                5, 1, Duration.ofSeconds(10), Duration.ofMinutes(10), maxBuckets, now::get);

        for (int i = 0; i < 50; i++) {
            now.addAndGet(1_000_000L); // 1ms advance
            long tokens = limiter.tryAcquire("client-" + i);
            assertThat(tokens).isEqualTo(5); // fresh bucket for each new client
        }

        // Map must be strictly capped at maxBuckets
        assertThat(limiter.trackedClientCount()).isLessThanOrEqualTo(maxBuckets);
    }

    @Test
    @DisplayName("Huge elapsed time does not cause arithmetic overflow or negative tokens")
    void hugeElapsedTimeDoesNotOverflow() {
        TokenBucketRateLimiter limiter = limiter(10, 5, 1);

        assertThat(limiter.tryAcquire("client1")).isEqualTo(10);
        // Advance clock by a huge duration (10 years)
        long tenYearsNanos = 10L * 365 * 24 * 3600 * 1_000_000_000L;
        now.addAndGet(tenYearsNanos);

        // Tokens must be fully refilled and capped at capacity, not overflowed to negative
        assertThat(limiter.tryAcquire("client1")).isEqualTo(10);
        assertThat(limiter.tryAcquire("client1")).isEqualTo(9);
    }

    @Test
    @DisplayName("Null client key is handled safely without NullPointerException")
    void nullClientKeyHandledSafely() {
        TokenBucketRateLimiter limiter = limiter(2, 1, 10);

        assertThat(limiter.tryAcquire(null)).isEqualTo(2);
        assertThat(limiter.tryAcquire(null)).isEqualTo(1);
        assertThat(limiter.tryAcquire(null)).isZero();

        assertThat(limiter.retryAfterSeconds(null)).isEqualTo(0);
    }

    @Test
    @DisplayName("Janitor is properly closed by AutoCloseable close()")
    void janitorClosesCleanly() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(
                10, 2, Duration.ofSeconds(1), Duration.ofSeconds(10));
        // Must close without throwing
        limiter.close();
    }
}
