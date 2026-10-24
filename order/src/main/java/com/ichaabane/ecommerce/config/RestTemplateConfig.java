package com.ichaabane.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for outgoing calls to the product service
 * ({@code ProductClient.purchaseProducts}).
 *
 * <p>Explicit timeouts have been configured (review finding R-3 in
 * {@code log-review.md}): without them a hung TCP connection would hold a
 * request thread (and its MDC logging context) indefinitely, and the
 * {@code ResourceAccessException} error logging in {@code ProductClient}
 * could never fire.</p>
 *
 * <p>Values, environment-overridable so ops can tune per environment without
 * a rebuild (defaults via {@code @Value} fallbacks):
 * <ul>
 *   <li>{@code order.http.connect-timeout-ms} (default 2000) — failure to
 *       open a connection is fatal fast; the caller signals "downstream
 *       unreachable" after 2s.</li>
 *   <li>{@code order.http.read-timeout-ms} (default 5000) — the purchase
 *       call is interactive (order creation blocks on it) and the payload
 *       is small; 5s covers the stock-decrement transaction plus latency.</li>
 * </ul></p>
 *
 * <p>Retry behavior is deliberately unchanged: {@code RestTemplate} retries
 * nothing by itself; error translation stays in {@code ProductClient}.</p>
 */
@Configuration
public class RestTemplateConfig {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 2000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 5000;

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public RestTemplateConfig(
            @Value("${order.http.connect-timeout-ms:2000}") int requestedConnectTimeoutMs,
            @Value("${order.http.read-timeout-ms:5000}") int requestedReadTimeoutMs) {
        // Non-positive overrides fall back to the documented defaults.
        this.connectTimeoutMs = requestedConnectTimeoutMs > 0 ? requestedConnectTimeoutMs : DEFAULT_CONNECT_TIMEOUT_MS;
        this.readTimeoutMs = requestedReadTimeoutMs > 0 ? requestedReadTimeoutMs : DEFAULT_READ_TIMEOUT_MS;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(httpRequestFactory());
    }

    /**
     * Builds the timeout-carrying request factory. Package-private for tests:
     * {@link SimpleClientHttpRequestFactory} exposes no timeout getters on
     * Spring Framework 6.0 (they arrive in 6.1), so the effective values are
     * asserted through {@link #getConnectTimeoutMs()}/{@link #getReadTimeoutMs()}.
     */
    SimpleClientHttpRequestFactory httpRequestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return factory;
    }

    // Effective values, visible for tests (see note above on missing getters).
    int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    int getReadTimeoutMs() {
        return readTimeoutMs;
    }
}
