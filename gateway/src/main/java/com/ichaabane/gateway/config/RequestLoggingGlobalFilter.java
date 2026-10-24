package com.ichaabane.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Global gateway filter for request logging and correlation ID propagation.
 *
 * <p>Assigns or forwards an {@code X-Correlation-Id} header across the platform,
 * measures routing duration, and emits structured logs on completion. Health check
 * endpoints are excluded to keep log volume clean.</p>
 *
 * <p>Log noise policy (review finding P-1 in {@code log-review.md}): the per-request
 * entry line and routine (non-error) completions are emitted at {@code DEBUG};
 * server-side (5xx) completions escalate to {@code WARN} and routing failures stay
 * at {@code ERROR}. Security events — rate-limit rejections (logged by
 * {@code RateLimitWebFilter}) and authentication/authorization failures (logged by
 * {@code GatewaySecurityConfig}) — continue to be logged at {@code WARN} there,
 * independent of this filter, so the transport log is quiet but security evidence
 * is never dropped.</p>
 *
 * <p>A client-supplied correlation ID is trusted only when it is safe to write
 * verbatim into logs and headers (8–64 chars of {@code [A-Za-z0-9._-]}); CR/LF,
 * control characters and oversized values are replaced by a generated UUID,
 * preventing log forging via request headers (finding S-2). Tracing
 * (Micrometer/Brave) attribution is unaffected — it uses its own headers.</p>
 */
@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingGlobalFilter.class);

    /** Trust list for client-provided correlation IDs (see class javadoc). */
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    /** Returns the candidate only when it is safe to use/log; otherwise {@code null}. */
    static String sanitize(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        return SAFE_CORRELATION_ID.matcher(trimmed).matches() ? trimmed : null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip health probes from logging noise
        if (path.startsWith("/actuator/health")) {
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();
        String correlationId = sanitize(request.getHeaders().getFirst(CORRELATION_ID_HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        final String finalCorrelationId = correlationId;

        // Propagate correlation ID downstream to microservices
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Ensure correlation ID is returned in the response headers to the client
        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, finalCorrelationId);
            return Mono.empty();
        });

        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        log.debug("Gateway routing request: {} {} [correlationId={}]", method, path, finalCorrelationId);

        return chain.filter(mutatedExchange)
                .doOnSuccess(v -> {
                    long duration = System.currentTimeMillis() - startTime;
                    var status = mutatedExchange.getResponse().getStatusCode();
                    int statusCode = status != null ? status.value() : 200;
                    if (status != null && status.is5xxServerError()) {
                        // Server error while routing/at the route target — actionable,
                        // keep it one level above routine traffic.
                        log.warn("Gateway routing ended with server error: {} {} status={} in {}ms [correlationId={}]",
                                method, path, statusCode, duration, finalCorrelationId);
                    } else {
                        // Routine completions (including 4xx client errors and
                        // rate-limited 429s — those are already logged at WARN by
                        // RateLimitWebFilter when they happen) stay at DEBUG so the
                        // transport log is quiet and the limiter's WARN is not doubled.
                        log.debug("Gateway completed request: {} {} status={} in {}ms [correlationId={}]",
                                method, path, statusCode, duration, finalCorrelationId);
                    }
                })
                .doOnError(err -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("Gateway request failed: {} {} after {}ms [correlationId={}]: {}",
                            method, path, duration, finalCorrelationId, err.getMessage(), err);
                });
    }
}
