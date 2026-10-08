package com.ichaabane.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the gateway's edge-security behavior (WebFlux stack):
 *
 * <ul>
 *   <li>every documented route requires authentication (no public business routes),</li>
 *   <li>a missing, malformed, or undecodable Bearer token yields 401,</li>
 *   <li>a valid JWT passes the gateway,</li>
 *   <li>CSRF is disabled: state-changing requests with a valid token are not
 *       rejected by the CSRF filter (stateless Bearer-token API).</li>
 * </ul>
 *
 * <p>Signature validation itself is delegated to Spring Security's
 * {@code ReactiveJwtDecoder} (issuer + JWKS discovery from the realm's OIDC
 * metadata). The decoder is stubbed here so no live Keycloak is required for
 * these tests; tokens containing {@code invalid} simulate a failed decode
 * (bad signature, expired token, wrong issuer, …).</p>
 */
@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
    // Deterministic local config: bypass eureka/config-server, fixed issuer.
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9098/realms/micro-services"
})
@DisplayName("Gateway security (JWT resource server) tests")
class GatewaySecurityConfigTest {

    @TestConfiguration
    static class StubConfig {

        /**
         * Reactive decoder stub (the gateway is WebFlux). Tokens containing
         * "invalid" simulate a rejected token (bad signature, expiry, wrong
         * issuer) with {@link BadJwtException} — the same exception family the
         * real Nimbus decoder throws, which Spring Security maps to 401.
         * Every other token authenticates as test-user.
         */
        @Bean
        @Primary
        public ReactiveJwtDecoder jwtDecoder() {
            ReactiveJwtDecoder decoder = mock(ReactiveJwtDecoder.class);
            when(decoder.decode(anyString())).thenAnswer(inv -> {
                String token = inv.getArgument(0);
                if (token.contains("invalid")) {
                    return Mono.error(new BadJwtException("Token is not valid (stubbed decoder)"));
                }
                return Mono.just(Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("test-user")
                    .build());
            });
            return decoder;
        }
    }

    @Autowired
    private WebTestClient client;

    // --- Authentication -------------------------------------------------------

    @Test
    @DisplayName("Protected route without a token returns 401")
    void protectedRouteWithoutTokenIs401() {
        client.get().uri("/api/v1/customers")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Malformed Authorization header (not Bearer) returns 401")
    void malformedAuthorizationHeaderIs401() {
        client.get().uri("/api/v1/customers")
            .header("Authorization", "Basic dXNlcjpwYXNz")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Bearer header without a token value returns 401")
    void emptyBearerHeaderIs401() {
        client.get().uri("/api/v1/customers")
            .header("Authorization", "Bearer ")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Token rejected by the JWT decoder (bad signature/expiry/issuer) returns 401")
    void undecodableTokenIs401() {
        client.get().uri("/api/v1/customers")
            .header("Authorization", "Bearer invalid-token-signature")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Every documented business route requires authentication")
    void allRoutesRequireAuthentication() {
        String[] protectedPaths = {
            "/api/v1/customers",
            "/api/v1/products",
            "/api/v1/orders",
            "/api/v1/order-lines/order/1",
            "/api/v1/payments"
        };
        for (String path : protectedPaths) {
            client.get().uri(path)
                .exchange()
                .expectStatus().isUnauthorized();
        }
    }

    // --- Actuator health (public readiness probe) ------------------------------

    @Test
    @DisplayName("/actuator/health is public (no token needed) — start-system.sh polls it")
    void actuatorHealthIsPublic() {
        client.get().uri("/actuator/health")
            .exchange()
            // Spring Boot Actuator returns 200 UP when the app context is healthy.
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("/actuator (discovery) stays authenticated (health only is public)")
    void otherActuatorEndpointsStayProtected() {
        client.get().uri("/actuator")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    // --- Valid-token pass-through ----------------------------------------------

    @Test
    @DisplayName("Valid Bearer token passes the gateway (not rejected at the edge)")
    void validTokenPasses() {
        client.get().uri("/api/v1/customers")
            .header("Authorization", "Bearer valid-token")
            .exchange()
            // The gateway accepts the token and forwards; the routed downstream
            // is not running in this test, so any non-401/403 outcome proves the
            // request was not stopped by security. A stubbed downstream endpoint
            // would be ideal, but Spring Cloud Gateway route filtering in a
            // sliced test returns 404/503 only AFTER authentication succeeded.
            .expectStatus().value(status -> org.junit.jupiter.api.Assertions
                .assertNotEquals(401, status.intValue()))
            .expectStatus().value(status -> org.junit.jupiter.api.Assertions
                .assertNotEquals(403, status.intValue()));
    }

    // --- Stateless API behavior (CSRF) -------------------------------------------

    @Test
    @DisplayName("POST with a valid token is not blocked by CSRF (stateless Bearer API)")
    void postWithValidTokenIsNotBlockedByCsrf() {
        client.post().uri("/api/v1/customers")
            .header("Authorization", "Bearer valid-token")
            .header("Content-Type", "application/json")
            .bodyValue("{}")
            .exchange()
            // With CSRF enabled this would be 403 before reaching the route.
            .expectStatus().value(status -> org.junit.jupiter.api.Assertions
                .assertNotEquals(403, status.intValue()));
    }

    @Test
    @DisplayName("DELETE without a token returns 401 (not CSRF 403)")
    void deleteWithoutTokenIs401() {
        client.delete().uri("/api/v1/customers/123")
            .exchange()
            .expectStatus().isUnauthorized();
    }
}
