package com.ichaabane.gateway.ratelimit;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Token-bucket rate limiter keyed by client, pure in-memory.
 *
 * <p>Allows short bursts up to {@code capacity} tokens, refilling
 * {@code refillTokens} tokens every {@code refillInterval}. All mutable bucket
 * state (tokens, refill base, last access) lives in a single {@link Bucket}
 * object per key and is mutated atomically inside
 * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)}.
 * Concurrent requests for the same client therefore cannot lose updates or
 * exceed the configured limit, while different clients never block each other
 * (no global lock).</p>
 *
 * <p>Memory is bounded by a background janitor thread that periodically drops
 * buckets untouched for longer than {@code idleEviction}. The sweep runs
 * entirely off the request path, so no request ever pays for a full-map scan
 * (the previous implementation swept from {@code tryAcquire}). The janitor is
 * the only thread this class owns; it is a daemon thread.</p>
 *
 * <p>Scope (deliberate): this limits per gateway instance only. Correct for the
 * current single-instance deployment; if the gateway is horizontally scaled,
 * move to a shared-store limiter (Redis) — not before.</p>
 *
 * <p>Time is read from a monotonic source ({@link System#nanoTime}) so
 * wall-clock adjustments cannot make tokens appear or vanish.</p>
 */
public class TokenBucketRateLimiter implements AutoCloseable {

    /** Immutable per-client bucket state; published atomically via a volatile reference. */
    static final class BucketState {
        final long tokens;          // remaining tokens
        final long lastRefillNanos; // timestamp the current token count was computed from
        final long lastAccessNanos; // last request timestamp for this client (used for eviction)

        BucketState(long tokens, long lastRefillNanos, long lastAccessNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
            this.lastAccessNanos = lastAccessNanos;
        }
    }

    /** Mutable bucket wrapper holding the volatile state reference. */
    static final class Bucket {
        volatile BucketState state;

        Bucket(BucketState state) {
            this.state = state;
        }
    }

    private final long capacity;
    private final long refillTokens;
    private final long refillIntervalNanos;
    private final long idleEvictionNanos;
    private final int maxBuckets;
    private final LongSupplier clockNanos;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Background janitor; {@code null} in deterministic test instances. */
    private final ScheduledExecutorService janitor;

    /**
     * Production constructor: monotonic clock + background eviction + default maxBuckets.
     *
     * @param capacity       max burst size in requests (initial + max tokens)
     * @param refillTokens   tokens added every {@code refillInterval}
     * @param refillInterval refill interval
     * @param idleEviction   remove entries unused for this long (memory bound)
     */
    public TokenBucketRateLimiter(int capacity, int refillTokens, Duration refillInterval,
                                  Duration idleEviction) {
        this(capacity, refillTokens, refillInterval, idleEviction, 50_000, System::nanoTime, true);
    }

    /**
     * Production constructor with explicit maxBuckets.
     */
    public TokenBucketRateLimiter(int capacity, int refillTokens, Duration refillInterval,
                                  Duration idleEviction, int maxBuckets) {
        this(capacity, refillTokens, refillInterval, idleEviction, maxBuckets, System::nanoTime, true);
    }

    /**
     * Deterministic constructor: injected clock, no janitor thread. Tests drive
     * eviction explicitly via {@link #evictIdleEntries(long)}.
     */
    TokenBucketRateLimiter(int capacity, int refillTokens, Duration refillInterval,
                           Duration idleEviction, LongSupplier clockNanos) {
        this(capacity, refillTokens, refillInterval, idleEviction, 50_000, clockNanos, false);
    }

    TokenBucketRateLimiter(int capacity, int refillTokens, Duration refillInterval,
                           Duration idleEviction, int maxBuckets, LongSupplier clockNanos) {
        this(capacity, refillTokens, refillInterval, idleEviction, maxBuckets, clockNanos, false);
    }

    private TokenBucketRateLimiter(int capacity, int refillTokens, Duration refillInterval,
                                   Duration idleEviction, int maxBuckets, LongSupplier clockNanos,
                                   boolean backgroundCleanup) {
        if (capacity < 1) {
            throw new IllegalArgumentException("rate-limit.capacity must be >= 1 (was " + capacity + ")");
        }
        if (refillTokens < 1) {
            throw new IllegalArgumentException("rate-limit.refill must be >= 1 (was " + refillTokens + ")");
        }
        if (refillInterval == null || refillInterval.isZero() || refillInterval.isNegative()) {
            throw new IllegalArgumentException("rate-limit.interval-seconds must be positive");
        }
        if (idleEviction == null || idleEviction.isZero() || idleEviction.isNegative()) {
            throw new IllegalArgumentException("rate-limit.eviction-seconds must be positive");
        }
        if (maxBuckets < 1) {
            throw new IllegalArgumentException("rate-limit.max-buckets must be >= 1 (was " + maxBuckets + ")");
        }
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillIntervalNanos = refillInterval.toNanos();
        this.idleEvictionNanos = idleEviction.toNanos();
        this.maxBuckets = maxBuckets;
        this.clockNanos = clockNanos;
        this.janitor = backgroundCleanup ? startJanitor() : null;
    }

    private ScheduledExecutorService startJanitor() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "rate-limit-janitor");
            thread.setDaemon(true);
            return thread;
        });
        // Sweep regularly (at least once per minute, or half the eviction window if shorter)
        // so buckets are reclaimed promptly without scanning more often than necessary.
        long periodNanos = Math.min(TimeUnit.SECONDS.toNanos(60),
                Math.max(TimeUnit.SECONDS.toNanos(1), idleEvictionNanos / 2));
        executor.scheduleWithFixedDelay(
                () -> evictIdleEntries(clockNanos.getAsLong()),
                periodNanos, periodNanos, TimeUnit.NANOSECONDS);
        return executor;
    }

    /**
     * Tries to take one token for the given client.
     *
     * @return count of tokens available BEFORE this request when it is allowed
     *         ({@code >= 1}), or {@code 0} when the client is over the limit and
     *         must be rejected with 429. Rejected requests consume no token and
     *         never mutate the bucket's availability.
     */
    public long tryAcquire(String clientKey) {
        String key = clientKey != null ? clientKey : "unknown";
        long now = clockNanos.getAsLong();
        ensureCapacity(key, now);

        long[] availableBefore = {0};
        buckets.compute(key, (k, bucket) -> {
            long currentTokens;
            long refillBase;
            if (bucket == null) {
                currentTokens = capacity;
                refillBase = now;
                bucket = new Bucket(new BucketState(capacity, now, now));
            } else {
                BucketState prev = bucket.state;
                currentTokens = prev.tokens;
                refillBase = prev.lastRefillNanos;
            }

            long elapsed = now - refillBase;
            long tokens = currentTokens;
            long newRefillBase = refillBase;

            if (elapsed >= refillIntervalNanos) {
                long intervals = elapsed / refillIntervalNanos;
                if (intervals >= capacity) {
                    tokens = capacity;
                    newRefillBase = now;
                } else {
                    tokens = Math.min(capacity, currentTokens + intervals * refillTokens);
                    newRefillBase = refillBase + intervals * refillIntervalNanos;
                }
            }

            long tokensAfter = tokens;
            if (tokens > 0) {
                availableBefore[0] = tokens;
                tokensAfter = tokens - 1;
            }

            bucket.state = new BucketState(tokensAfter, newRefillBase, now);
            return bucket;
        });
        return availableBefore[0];
    }

    /**
     * Seconds until the key is allowed again (for the {@code Retry-After}
     * header). Pure: computes the wait, never mutates bucket state.
     */
    public long retryAfterSeconds(String clientKey) {
        if (clientKey == null) {
            return 0;
        }
        Bucket bucket = buckets.get(clientKey);
        if (bucket == null) {
            return 0;
        }
        BucketState state = bucket.state;
        long now = clockNanos.getAsLong();
        long elapsed = now - state.lastRefillNanos;
        if (elapsed >= refillIntervalNanos || state.tokens > 0) {
            return 0;
        }
        long nextRefillNanos = state.lastRefillNanos + refillIntervalNanos;
        long nanos = nextRefillNanos - now;
        if (nanos <= 0) {
            return 1;
        }
        return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L); // ceil to seconds, min 1
    }

    /** Current number of tracked clients (exposed for tests). */
    int trackedClientCount() {
        return buckets.size();
    }

    /**
     * Bounds the bucket map in O(1) time when capacity is reached, preventing
     * unbounded retention under high-cardinality traffic without scanning the
     * whole map on the request path.
     */
    private void ensureCapacity(String key, long now) {
        if (buckets.size() < maxBuckets || buckets.containsKey(key)) {
            return;
        }
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        Map.Entry<String, Bucket> oldest = null;
        int sampled = 0;
        while (it.hasNext() && sampled < 16) {
            Map.Entry<String, Bucket> candidate = it.next();
            Bucket bucket = candidate.getValue();
            if (bucket != null) {
                BucketState s = bucket.state;
                if (now - s.lastAccessNanos > idleEvictionNanos) {
                    buckets.remove(candidate.getKey(), bucket);
                    return;
                }
                if (oldest == null || s.lastAccessNanos < oldest.getValue().state.lastAccessNanos) {
                    oldest = candidate;
                }
            }
            sampled++;
        }
        if (oldest != null) {
            buckets.remove(oldest.getKey(), oldest.getValue());
        }
    }

    /**
     * Drops buckets idle longer than the eviction window. Runs on the janitor
     * thread in production and is called directly by deterministic tests.
     * Removal is conditional on the bucket instance so a concurrent
     * {@code tryAcquire} that replaced the entry is never dropped.
     */
    void evictIdleEntries(long nowNanos) {
        for (Map.Entry<String, Bucket> entry : buckets.entrySet()) {
            Bucket bucket = entry.getValue();
            if (bucket != null && (nowNanos - bucket.state.lastAccessNanos > idleEvictionNanos)) {
                buckets.remove(entry.getKey(), bucket);
            }
        }
    }

    /** Stops the background janitor (no-op for deterministic test instances). */
    @Override
    public void close() {
        if (janitor != null) {
            janitor.shutdownNow();
        }
    }
}
