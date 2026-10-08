# Keycloak Authentication — API Gateway

This document describes the current Keycloak authentication implementation for the API Gateway.

## 1. Overview

Keycloak is the Identity Provider (IdP).

The API Gateway acts as an OAuth2/OIDC resource server and validates Bearer JWTs before forwarding requests to the microservices.

Current implementation:

* Spring Security WebFlux
* `spring-boot-starter-oauth2-resource-server`
* Keycloak realm: `micro-services`
* JWT validation handled by Spring Security/Nimbus
* No custom JWT decoding
* No role or scope authorization is currently configured
* Business services do not currently authenticate callers independently

Authentication is implemented at the gateway level. A valid JWT is required for protected requests.

---

## 2. Architecture

```text
Client
  |
  | Authorization: Bearer <JWT>
  v
API Gateway :8222
  |
  | JWT validation
  | - signature
  | - issuer
  | - expiration
  v
Keycloak :9098
  |
  | OIDC metadata / JWKS
  v
Gateway
  |
  | authenticated request
  v
Microservices
  |
  +-- customer-service :8090
  +-- product-service  :8050
  +-- order-service    :8070
  +-- payment-service  :8060
  +-- notification-service :8040
```

The gateway is currently executed on the host rather than as a Docker container.

---

## 3. Keycloak Configuration

| Setting        | Current value                      |
| -------------- | ---------------------------------- |
| Image          | `quay.io/keycloak/keycloak:24.0.2` |
| Container      | `keycloak-ms`                      |
| Docker service | `keycloak`                         |
| Docker network | `microservices-net`                |
| Host port      | `9098`                             |
| Container port | `8080`                             |
| Realm          | `micro-services`                   |
| Startup mode   | `start-dev`                        |

Keycloak is currently configured for development.

The realm is created manually through the Keycloak console and is not currently versioned in the repository.

---

## 4. OIDC Issuer

The gateway currently uses:

```text
http://localhost:9098/realms/micro-services
```

as its configured issuer URI.

The issuer URI has an important security role: it is used by Spring Security both for OIDC discovery and for validating the JWT `iss` claim.

The JWT issuer must therefore match the configured issuer:

```text
JWT `iss`
    =
spring.security.oauth2.resourceserver.jwt.issuer-uri
```

For the current host-based gateway deployment:

```text
http://localhost:9098/realms/micro-services
```

is the configured issuer.

---

## 5. Docker Deployment and Issuer Handling

The gateway currently runs on the host, so it reaches Keycloak through:

```text
http://localhost:9098
```

If the gateway is later containerized, do **not** simply replace the issuer with:

```text
http://keycloak:8080/realms/micro-services
```

The reason is that `keycloak:8080` is the Docker network address, while the issuer is also an identity value contained in the JWT.

For example, if Keycloak issues:

```json
{
  "iss": "http://localhost:9098/realms/micro-services"
}
```

then configuring:

```yaml
issuer-uri: http://keycloak:8080/realms/micro-services
```

would cause issuer validation to fail.

### Rule

The configured `issuer-uri` must always match the JWT `iss` claim.

Container networking and issuer identity must therefore be treated as separate concerns:

```text
Issuer identity
    |
    +-- must match JWT `iss`

Network connectivity
    |
    +-- gateway must reach OIDC metadata
    +-- gateway must reach JWKS
```

If the gateway is containerized, Keycloak must be configured so that the issuer advertised in its OIDC metadata and included in issued tokens is reachable and consistent with the gateway's configured issuer.

Do not change `issuer-uri` to an internal Docker hostname unless Keycloak is intentionally configured to issue tokens with that hostname as the issuer.

---

## 6. JWT Validation

JWT validation is handled by Spring Security.

| Validation                 | Implementation                    |
| -------------------------- | --------------------------------- |
| Signature                  | Validated using Keycloak JWKS     |
| Issuer                     | JWT `iss` must match `issuer-uri` |
| Expiration                 | Nimbus/Spring Security validation |
| Not-before                 | Nimbus/Spring Security validation |
| Malformed token            | Rejected                          |
| Invalid token              | `401 Unauthorized`                |
| Infrastructure/IdP failure | Server-side failure               |

JWKS information is discovered through the OIDC metadata associated with the configured issuer.

The application does not manually decode JWTs or trust claims without validation.

---

## 7. Gateway Security

Current security configuration:

```java
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
        .oauth2ResourceServer(oauth2 ->
            oauth2.jwt(Customizer.withDefaults())
        );

    return http.build();
}
```

### CSRF

CSRF is disabled because the gateway currently uses stateless Bearer-token authentication rather than browser cookies or server-side sessions.

The authentication credential is supplied through:

```http
Authorization: Bearer <JWT>
```

---

## 8. Authorization

Role and scope authorization is **not currently implemented**.

There is currently:

* no `JwtAuthenticationConverter`
* no Keycloak role extraction
* no `hasRole(...)`
* no `hasAuthority(...)`
* no route-specific role restrictions

Therefore:

```text
Authentication:
    implemented

Authorization:
    not implemented
```

Any valid token accepted by the gateway can currently access the authenticated routes.

Authorization should be added before the system is exposed to users who require different permission levels.

---

## 9. Gateway Routes

The gateway currently defines these business routes:

| Route                    | Service          | Authentication |
| ------------------------ | ---------------- | -------------- |
| `/api/v1/customers/**`   | customer-service | Required       |
| `/api/v1/products/**`    | product-service  | Required       |
| `/api/v1/orders/**`      | order-service    | Required       |
| `/api/v1/order-lines/**` | order-service    | Required       |
| `/api/v1/payments/**`    | payment-service  | Required       |

Discovery locator is disabled, so routes are explicitly configured.

There are currently no public business routes.

The gateway uses:

```text
anyExchange().authenticated()
```

so unknown routes are also protected.

---

## 10. Direct Microservice Access

The gateway is the intended security entry point, but the current microservices expose their own ports:

```text
customer-service     :8090
product-service      :8050
order-service        :8070
payment-service      :8060
notification-service :8040
```

The current documentation identifies these services as accepting unauthenticated direct traffic.

This means an attacker who can directly reach a service can potentially bypass gateway authentication.

Therefore, the current architecture should be considered:

```text
Gateway authentication
        +
Unauthenticated internal services
```

Before production exposure, direct service access should be prevented through network isolation or service-level authentication.

---

## 11. Docker Configuration

Current Keycloak configuration:

```yaml
keycloak:
  container_name: keycloak-ms
  image: quay.io/keycloak/keycloak:24.0.2

  ports:
    - "9098:8080"

  environment:
    KEYCLOAK_ADMIN: ${KEYCLOAK_ADMIN:-admin}
    KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-admin}

  networks:
    - microservices-net

  command:
    - "start-dev"
```

The gateway is currently started from the host, so:

```text
Gateway
   |
   | localhost:9098
   v
Keycloak container :8080
```

This deployment explains why the current issuer is:

```text
http://localhost:9098/realms/micro-services
```

---

## 12. Environment Configuration

Current gateway configuration:

```text
KEYCLOAK_ISSUER_URI
    http://localhost:9098/realms/micro-services
```

Keycloak administrator configuration:

```text
KEYCLOAK_ADMIN
KEYCLOAK_ADMIN_PASSWORD
```

The administrator credentials have development defaults and must not be reused in a real environment.

---

## 13. Authentication Flow

```text
1. Client obtains an access token from Keycloak.

2. Client sends the token to the gateway:

   Authorization: Bearer <JWT>

3. Gateway extracts the Bearer token.

4. Spring Security validates the JWT:

   - signature
   - issuer
   - expiration
   - token validity

5. Invalid token:
   401 Unauthorized

6. Valid token:
   request is authenticated.

7. Gateway forwards the request to the target microservice.
```

---

## 14. Local Development

Start Keycloak:

```bash
docker compose up -d keycloak
```

Or start the complete infrastructure:

```bash
docker compose up -d
```

OIDC metadata can be checked with:

```bash
curl http://localhost:9098/realms/micro-services/.well-known/openid-configuration
```

The response should contain the realm's OIDC metadata, including the issuer and JWKS endpoint.

---

## 15. Getting a Development Token

The current development setup uses a token-issuing Keycloak client.

Example:

```bash
curl -X POST \
  "http://localhost:9098/realms/<KEYCLOAK_REALM>/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=<KEYCLOAK_CLIENT_ID>" \
  -d "username=<DEV_USERNAME>" \
  -d "password=<DEV_PASSWORD>"
```

Use development credentials only.

Never commit real credentials, tokens, or secrets to the repository.

---

## 16. API Testing

### Request without token

```bash
curl -i http://localhost:8222/api/v1/customers
```

Expected:

```text
401 Unauthorized
```

### Request with invalid token

```bash
curl -i \
  -H "Authorization: Bearer not-a-jwt" \
  http://localhost:8222/api/v1/customers
```

Expected:

```text
401 Unauthorized
```

### Request with valid token

```bash
curl -i \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  http://localhost:8222/api/v1/customers
```

The request should pass gateway authentication and be forwarded to the customer service.

---

## 17. Tests

Gateway security tests are located at:

```text
gateway/src/test/java/com/ichaabane/gateway/config/GatewaySecurityConfigTest.java
```

The current tests cover authentication-related scenarios including:

* missing token
* malformed/invalid token
* protected routes
* valid-token requests
* state-changing requests with CSRF disabled

---

## 18. Troubleshooting

### 401 Unauthorized

Check:

1. The `Authorization` header is present.
2. The token is not expired.
3. The token is validly signed by Keycloak.
4. The JWT `iss` claim exactly matches `KEYCLOAK_ISSUER_URI`.
5. The gateway can reach the OIDC metadata endpoint.
6. The gateway can reach the JWKS endpoint.

Check the issuer inside the token:

```text
JWT:
    iss = http://localhost:9098/realms/micro-services
```

It must match:

```text
KEYCLOAK_ISSUER_URI
    =
http://localhost:9098/realms/micro-services
```

### Gateway cannot reach Keycloak

For the current host-based deployment, verify:

```bash
docker compose ps keycloak
```

and:

```bash
docker compose logs keycloak
```

Then verify OIDC metadata:

```bash
curl \
  http://localhost:9098/realms/micro-services/.well-known/openid-configuration
```

If the gateway is containerized, verify that the container can resolve and reach the configured issuer and the endpoints advertised by its OIDC metadata.

Do not fix a connectivity problem by changing the issuer blindly.

---

## 19. Current Security Limitations

The current implementation has the following important limitations:

### No authorization

Any valid realm token can access authenticated business routes.

### Direct service access

The microservices can currently be reached directly without going through the gateway.

### No TLS

The development setup uses HTTP.

Production deployments must protect tokens in transit with TLS.

### Development Keycloak

Keycloak uses:

```text
start-dev
```

and development administrator credentials.

This configuration is not suitable for production.

### Realm configuration is not versioned

The `micro-services` realm is currently configured manually in Keycloak.

This can cause configuration drift between environments.

---

## 20. Production Requirements

Before production exposure, address at least these items:

1. Use TLS.
2. Remove development Keycloak credentials.
3. Use a production Keycloak configuration.
4. Implement role/scope authorization.
5. Prevent direct unauthenticated access to microservices.
6. Make the Keycloak realm configuration reproducible.
7. Keep the JWT issuer consistent across Keycloak, the gateway, and deployed environments.
8. Verify gateway-to-Keycloak connectivity for OIDC metadata and JWKS.
9. Do not change `issuer-uri` to a Docker service name unless that value is intentionally used as the JWT issuer.

---

## 21. Key Rule

The most important rule when deploying the gateway with Docker is:

```text
             JWT
              |
              v
        +-------------+
        |     iss     |
        +-------------+
              |
              | must equal
              v
        +-------------+
        | issuer-uri  |
        +-------------+
```

The Docker hostname:

```text
keycloak:8080
```

is a **network address**.

The JWT issuer:

```text
http://localhost:9098/realms/micro-services
```

is an **OIDC identity**.

They must not be treated as interchangeable values.

Changing the gateway's `issuer-uri` only to make Docker networking work can break JWT issuer validation.

The deployment must instead ensure that:

```text
Keycloak issuer
      =
JWT `iss`
      =
Gateway `issuer-uri`
```

while also ensuring that the gateway can reach the OIDC metadata and JWKS endpoints from its runtime environment.
