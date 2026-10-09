package com.ichaabane.gateway.config;

import java.time.Duration;
import java.util.List;

/**
 * Rate-limit settings bound from {@code rate-limit.*} (application.yml,
 * env-overridable). A small validated value object — a full
 * {@code @ConfigurationProperties} class is not warranted here.
 *
 * <ul>
 *   <li>{@code capacity} — burst size (max tokens a client may accumulate).</li>
 *   <li>{@code refill} / {@code interval-seconds} — tokens added per interval.</li>
 *   <li>{@code eviction-seconds} — idle window after which a client bucket is
 *       dropped by the janitor (memory bound under high-cardinality traffic).</li>
 *   <li>{@code trust-forwarded-headers} + {@code trusted-proxies} — opt-in
 *       forwarded-header trust, honoured only from the listed peer addresses.</li>
 *   <li>{@code exempt-loopback} — skip limiting for loopback peers (internal
 *       service-to-gateway traffic); defaults off so the limiter cannot be
 *       silently disabled in the local (loopback) deployment shape.</li>
 * </ul>
 *
 * <p>Validation rejects nonsensical values at startup instead of silently
 * disabling protection.</p>
 */
public class RateLimitProperties {

    final int capacity;
    final int refill;
    final int intervalSeconds;
    final int evictionSeconds;
    final int maxBuckets;
    final boolean trustForwardedHeaders;
    final List<String> trustedProxies;
    final boolean exemptLoopback;

    public RateLimitProperties(int capacity, int refill, int intervalSeconds,
                               int evictionSeconds, int maxBuckets, boolean trustForwardedHeaders,
                               List<String> trustedProxies, boolean exemptLoopback) {
        if (capacity < 1) {
            throw new IllegalArgumentException("rate-limit.capacity must be >= 1 (was " + capacity + ")");
        }
        if (refill < 1) {
            throw new IllegalArgumentException("rate-limit.refill must be >= 1 (was " + refill + ")");
        }
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException("rate-limit.interval-seconds must be >= 1 (was " + intervalSeconds + ")");
        }
        if (evictionSeconds < 1) {
            throw new IllegalArgumentException("rate-limit.eviction-seconds must be >= 1 (was " + evictionSeconds + ")");
        }
        if (maxBuckets < 1) {
            throw new IllegalArgumentException("rate-limit.max-buckets must be >= 1 (was " + maxBuckets + ")");
        }
        this.capacity = capacity;
        this.refill = refill;
        this.intervalSeconds = intervalSeconds;
        this.evictionSeconds = evictionSeconds;
        this.maxBuckets = maxBuckets;
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
        this.exemptLoopback = exemptLoopback;
    }

    public RateLimitProperties(int capacity, int refill, int intervalSeconds,
                               int evictionSeconds, boolean trustForwardedHeaders,
                               List<String> trustedProxies, boolean exemptLoopback) {
        this(capacity, refill, intervalSeconds, evictionSeconds, 50_000,
             trustForwardedHeaders, trustedProxies, exemptLoopback);
    }

    Duration refillInterval() {
        return Duration.ofSeconds(intervalSeconds);
    }

    Duration evictionWindow() {
        return Duration.ofSeconds(evictionSeconds);
    }
}
