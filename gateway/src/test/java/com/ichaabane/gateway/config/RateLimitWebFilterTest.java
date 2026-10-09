package com.ichaabane.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rate limiting at the edge, exercised through the full gateway context so it
 * proves the filter runs BEFORE authentication/routing and returns the
 * documented HTTP contract:
 *
 * <ul>
 *   <li>/actuator/health is NEVER throttled (the startup script depends on it),</li>
 *   <li>requests beyond the bucket get 429 + a numeric Retry-After,</li>
 *   <li>the 429 body leaks no internal detail,</li>
 *   <li>rotating X-Forwarded-For cannot buy extra requests.</li>
 * </ul>
 *
 * <p>Deterministic-ish: a tiny burst (capacity 3) is bound via properties and
 * no refill occurs within a test run. Tests do not assume ordering — the
 * shared loopback bucket may already be partially consumed, so each test
 * requests until it observes a 429 rather than assuming a fixed request index.</p>
 */
@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9098/realms/micro-services",
    "rate-limit.capacity=3",
    "rate-limit.refill=1",
    "rate-limit.interval-seconds=600"
})
@DisplayName("Edge rate limiting (per client IP, 429 + Retry-After)")
class RateLimitWebFilterTest {

    @Autowired
    private WebTestClient client;

    @Test
    @DisplayName("Health endpoint and subpaths are never throttled (burst of 10 requests)")
    void healthIsNeverThrottled() {
        for (int i = 0; i < 10; i++) {
            client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
            client.get().uri("/actuator/health/custom")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(429));
        }
    }

    @Test
    @DisplayName("Requests beyond the burst get 429 with a numeric Retry-After")
    void limitedRouteGives429WithRetryAfter() {
        var isolated = client.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token").build();

        for (int i = 0; i < 12; i++) {
            var result = isolated.get().uri("/api/v1/products")
                .exchange()
                .returnResult(String.class);

            if (result.getStatus().value() == 429) {
                String retryAfter = result.getResponseHeaders().getFirst("Retry-After");
                assertThat(retryAfter).as("Retry-After must be present on 429").isNotNull();
                assertThat(retryAfter).matches("\\d+");
                return;
            }
        }
        fail("expected the limiter to reject within the burst window");
    }

    @Test
    @DisplayName("429 body carries no stack trace or internal details")
    void rateLimitBodyIsClean() {
        var isolated = client.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token").build();

        for (int i = 0; i < 12; i++) {
            var result = isolated.get().uri("/api/v1/products")
                .exchange()
                .expectBody(String.class)
                .returnResult();

            if (result.getStatus().value() == 429) {
                String body = result.getResponseBody();
                assertThat(body).isNotNull();
                assertThat(body).contains("\"status\":429");
                assertThat(body.toLowerCase()).doesNotContain("exception");
                return;
            }
        }
        fail("expected the limiter to reject within the burst window");
    }

    @Test
    @DisplayName("Rotating X-Forwarded-For cannot bypass the limit (headers untrusted by default)")
    void spoofedForwardingHeaderCannotBypass() {
        int throttled = 0;
        for (int i = 0; i < 10; i++) {
            var result = client.get().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .header("X-Forwarded-For", "203.0.113." + i)
                .exchange()
                .returnResult(String.class);
            if (result.getStatus().value() == 429) {
                throttled++;
            }
        }
        assertThat(throttled).as("a rotating spoofed header must not defeat the limiter").isGreaterThan(0);
    }

    @TestConfiguration
    static class StubConfig {

        /** Stubbed decoder: any non-"invalid" token authenticates (RBAC is not configured). */
        @Bean
        @Primary
        public ReactiveJwtDecoder jwtDecoder() {
            ReactiveJwtDecoder decoder = mock(ReactiveJwtDecoder.class);
            when(decoder.decode(anyString())).thenAnswer(inv -> {
                String token = inv.getArgument(0);
                if (token.contains("invalid")) {
                    return Mono.error(new BadJwtException("not valid (stub)"));
                }
                return Mono.just(Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("rate-test-user")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build());
            });
            return decoder;
        }
    }
}
