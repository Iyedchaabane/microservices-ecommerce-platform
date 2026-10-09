package com.ichaabane.gateway.config;

import com.ichaabane.gateway.ratelimit.ClientIpResolver;
import com.ichaabane.gateway.ratelimit.TokenBucketRateLimiter;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;

/**
 * Edge rate limiting: one token-bucket per client IP on the business routes. A
 * WebFilter (not a gateway route filter) so the limit applies BEFORE
 * authentication and routing — an abusive client burns no downstream capacity
 * and no JWKS round-trips.
 *
 * <p>Configuration ({@code rate-limit.*}, env-overridable, see
 * {@link RateLimitProperties}): burst capacity, refill rate/interval, idle
 * eviction window, trusted-proxy handling and optional loopback exemption.</p>
 *
 * <p>Exempt: {@code /actuator/health} (readiness probes must never be throttled
 * — the startup script depends on it) and {@code /eureka/**} (registry
 * traffic). Rejected requests get HTTP 429 with a {@code Retry-After} header
 * (seconds until a token is available) and a fixed, detail-free JSON body.</p>
 *
 * <p>Client identity is resolved by {@link ClientIpResolver}: the TCP peer
 * address unless forwarded-header trust is explicitly enabled for a configured
 * trusted proxy. Limiter state is per gateway instance (in-memory) — correct
 * for this single-instance deployment; a scaled-out gateway would need a shared
 * store (not added here).</p>
 */
public class RateLimitWebFilter implements WebFilter, Ordered, DisposableBean {

    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100; // run early, before security

    private final TokenBucketRateLimiter limiter;
    private final ClientIpResolver clientIdentity;

    public RateLimitWebFilter(RateLimitProperties properties) {
        this(
            new TokenBucketRateLimiter(properties.capacity, properties.refill,
                properties.refillInterval(), properties.evictionWindow(), properties.maxBuckets),
            new ClientIpResolver(properties.trustForwardedHeaders,
                new LinkedHashSet<>(properties.trustedProxies), properties.exemptLoopback)
        );
    }

    /** Test constructor with a prepared limiter and client resolver. */
    RateLimitWebFilter(TokenBucketRateLimiter limiter, ClientIpResolver clientIdentity) {
        this.limiter = limiter;
        this.clientIdentity = clientIdentity;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isExempt(path) || clientIdentity.isExempt(exchange)) {
            return chain.filter(exchange);
        }
        String clientKey = clientIdentity.resolve(exchange);

        if (limiter.tryAcquire(clientKey) > 0) {
            return chain.filter(exchange);
        }

        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set("Retry-After", String.valueOf(limiter.retryAfterSeconds(clientKey)));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"status\":429,\"error\":\"Too Many Requests\","
                + "\"message\":\"Rate limit exceeded; retry later\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private boolean isExempt(String path) {
        return path.equals("/actuator/health")
                || path.startsWith("/actuator/health/")
                || path.startsWith("/eureka/");
    }

    /** Releases the limiter's background janitor thread. */
    @Override
    public void destroy() {
        limiter.close();
    }
}
