package com.ichaabane.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Arrays;
import java.util.List;

/**
 * Security entry point of the platform.
 *
 * <p>The gateway is a stateless Bearer-token OAuth2 resource server: every
 * request must carry a JWT issued by Keycloak (issuer configured in
 * {@code application.yml}, signature verified through the realm's JWKS
 * endpoint). CSRF is disabled because there are no cookies and no server-side
 * sessions — a stolen CSRF token has no meaning when authentication relies
 * exclusively on an {@code Authorization: Bearer} header that browsers do not
 * attach automatically.</p>
 *
 * <p>All five documented routes ({@code /api/v1/customers|products|orders|
 * order-lines|payments/**}) require authentication; there are deliberately no
 * public business routes. {@code /actuator/health} is public so readiness
 * probes and the startup script can poll it without a Bearer token; every
 * other actuator endpoint stays authenticated. Role/authority mapping is not configured yet: any
 * valid, unexpired token from the realm is accepted (authentication only, no
 * authorization). Scope-based and role-based restrictions can be added here
 * per route when needed.</p>
 *
 * <p>Edge rate limiting is wired here as a {@link RateLimitWebFilter} bean; see
 * {@link RateLimitProperties} for the {@code rate-limit.*} settings.</p>
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

  /** Per-client-IP rate limiting on business routes (see RateLimitWebFilter). */
  @Bean
  public RateLimitWebFilter rateLimitWebFilter(
      @Value("${rate-limit.capacity:60}") int capacity,
      @Value("${rate-limit.refill:20}") int refill,
      @Value("${rate-limit.interval-seconds:10}") int intervalSeconds,
      @Value("${rate-limit.eviction-seconds:600}") int evictionSeconds,
      @Value("${rate-limit.max-buckets:50000}") int maxBuckets,
      @Value("${rate-limit.trust-forwarded-headers:false}") boolean trustForwardedHeaders,
      @Value("${rate-limit.trusted-proxies:}") String trustedProxies,
      @Value("${rate-limit.exempt-loopback:false}") boolean exemptLoopback) {
    RateLimitProperties properties = new RateLimitProperties(capacity, refill, intervalSeconds,
        evictionSeconds, maxBuckets, trustForwardedHeaders, parseTrustedProxies(trustedProxies), exemptLoopback);
    return new RateLimitWebFilter(properties);
  }

  /** Parses a comma-separated trusted-proxy list; blank entries are ignored. */
  private static List<String> parseTrustedProxies(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .toList();
  }

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(exchange -> exchange
            .pathMatchers("/eureka/**")
            .permitAll()
            .pathMatchers("/actuator/health", "/actuator/health/**")
            .permitAll()
            .anyExchange()
            .authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
  }
}
