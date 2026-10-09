package com.ichaabane.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rate-limit configuration must fail fast on nonsensical values instead of
 * silently serialising to an ineffective limiter.
 */
class RateLimitPropertiesTest {

    @Test
    @DisplayName("Valid settings expose the expected durations and defaults")
    void validSettings() {
        RateLimitProperties properties =
                new RateLimitProperties(60, 20, 10, 600, false, List.of(), false);

        assertThat(properties.capacity).isEqualTo(60);
        assertThat(properties.refill).isEqualTo(20);
        assertThat(properties.maxBuckets).isEqualTo(50_000);
        assertThat(properties.refillInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.evictionWindow()).isEqualTo(Duration.ofSeconds(600));
        assertThat(properties.trustedProxies).isEmpty();

        RateLimitProperties customMax =
                new RateLimitProperties(60, 20, 10, 600, 10_000, false, List.of(), false);
        assertThat(customMax.maxBuckets).isEqualTo(10_000);
    }

    @Test
    @DisplayName("A null trusted-proxy list is normalised to empty")
    void nullTrustedProxiesBecomeEmpty() {
        RateLimitProperties properties =
                new RateLimitProperties(1, 1, 1, 1, false, null, false);

        assertThat(properties.trustedProxies).isEmpty();
    }

    @Test
    @DisplayName("Invalid settings are rejected")
    void invalidSettingsAreRejected() {
        assertThatThrownBy(() -> new RateLimitProperties(0, 1, 10, 600, false, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties(1, 0, 10, 600, false, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties(1, 1, 0, 600, false, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties(1, 1, 10, 0, false, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties(1, 1, 10, 600, 0, false, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties(1, 1, 10, 600, -1, false, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
