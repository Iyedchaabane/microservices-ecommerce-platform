# Gateway Service

Spring Cloud Gateway (`:8222`) — the single entry point and **security edge** of
the platform.

## Responsibilities

* Routes the five documented API paths to the business services via Eureka
  load-balancing (`config-server/src/main/resources/configurations/gateway-service.yml`;
  discovery-locator auto-routing is disabled).
* **Authenticates every request** as an OAuth2 resource server: Bearer JWTs
  issued by Keycloak (realm `micro-services`) are validated by Spring Security
  (signature via the realm's JWKS, issuer, expiry). There are no public routes
  and no role-based restrictions yet.
* CSRF is disabled: the API is a stateless Bearer-token API (no cookies, no
  sessions).

## Security configuration

* Security chain: `src/main/java/com/ichaabane/gateway/config/GatewaySecurityConfig.java`
* Issuer: `spring.security.oauth2.resourceserver.jwt.issuer-uri` in
  `src/main/resources/application.yml`, env-overridable via `KEYCLOAK_ISSUER_URI`
  (dev default `http://localhost:9098/realms/micro-services`).

## Tests

`src/test/java/com/ichaabane/gateway/config/GatewaySecurityConfigTest.java`
verifies the edge behavior without a live Keycloak:

* missing / malformed / undecodable tokens → **401**
* all five documented routes require authentication
* a valid token passes the edge
* POST/DELETE are not blocked by CSRF with a valid/absent token

## Documentation

See [docs/security/keycloak.md](../docs/security/keycloak.md) for the full
Keycloak authentication documentation (architecture, flows, Docker config,
troubleshooting).
