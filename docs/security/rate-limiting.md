# Rate Limiting — API Gateway

This document describes the edge rate-limiting implementation for the API Gateway.

## 1. Overview

The gateway applies **per-client-IP rate limiting** on the business routes, before
authentication and routing.

Current implementation:

* In-memory token bucket, one bucket per client IP, per gateway instance
* Implemented as a `WebFilter` (`RateLimitWebFilter`) that runs **before** the
  Spring Security chain and before gateway routing
* Configuration bound from `rate-limit.*` properties (`RateLimitProperties`),
  all overridable with environment variables
* Rejected requests receive `429 Too Many Requests` with a numeric `Retry-After`
  header and a detail-free JSON body

Exempt paths:

* `/actuator/health` and `/actuator/health/**` — readiness probes and the
  startup script poll these and must never be throttled
* `/eureka/**` — registry traffic
* Loopback traffic when `rate-limit.exempt-loopback=true` (opt-in, see §9)

There is no rate limiting behind the gateway: the business services accept
direct traffic on their own ports (see
[keycloak.md §10 — Direct Microservice Access](keycloak.md)) and are not limited.

---

## 2. Request Flow

```text
Client
  |
  v
RateLimitWebFilter (Ordered = HIGHEST_PRECEDENCE + 100)
  |
  +-- /eureka/** or /actuator/health(/**)
  |         -> exempt, continue (probes and registry traffic)
  |
  +-- resolve client key (TCP peer, or trusted-proxy X-Forwarded-For)
  |
  +-- token available?
        +-- yes -> Security chain (JWT validation) -> gateway routes -> services
        +-- no  -> 429 + Retry-After (authentication and routing never happen)
```

Running before authentication has a deliberate benefit: an abusive client
consumes no downstream capacity and triggers no JWKS round-trips.

---

## 3. Algorithm

A classic **token bucket**:

| Parameter | Default | Meaning |
| --------- | ------- | ------- |
| `capacity` | 60 | Burst size: max tokens a bucket can hold (and the initial fill) |
| `refill` | 20 | Tokens added per interval |
| `interval-seconds` | 10 | Refill interval |

With the defaults this permits a burst of 60 requests, sustained at roughly
2 requests/second per client IP.

Properties of the algorithm:

* No fractional tokens: a partially elapsed interval grants nothing.
* Rejected requests consume **no** token and never move the refill base —
  hammering a throttled endpoint does not extend your wait.
* Refill accumulates while an idle client does nothing, but is always capped at
  `capacity` (long idle periods saturate, they do not stockpile).
* All bucket state lives in one immutable object per client, replaced atomically
  inside `ConcurrentHashMap.compute`. Concurrent requests for one IP cannot lose
  updates or exceed the limit; different clients never contend on a lock.
* Time is read from `System.nanoTime` (monotonic), so wall-clock adjustments
  cannot create or destroy tokens.
* Memory is bounded two ways: a background janitor ("rate-limit-janitor" daemon
  thread, off the request path) drops buckets idle for longer than
  `eviction-seconds`; a `max-buckets` cap keeps the map strictly bounded under
  high-cardinality traffic regardless of the janitor.

The implementation lives in
`gateway/src/main/java/com/ichaabane/gateway/ratelimit/TokenBucketRateLimiter.java`.

---

## 4. Configuration

All settings live under `rate-limit:` in `gateway/src/main/resources/application.yml`
with environment-variable overrides (dev defaults committed):

```yaml
rate-limit:
  capacity: ${RATE_LIMIT_CAPACITY:60}
  refill: ${RATE_LIMIT_REFILL:20}
  interval-seconds: ${RATE_LIMIT_INTERVAL_SECONDS:10}
  eviction-seconds: ${RATE_LIMIT_EVICTION_SECONDS:600}
  max-buckets: ${RATE_LIMIT_MAX_BUCKETS:50000}
  trust-forwarded-headers: ${RATE_LIMIT_TRUST_FORWARDED:false}
  trusted-proxies: ${RATE_LIMIT_TRUSTED_PROXIES:}
  exempt-loopback: ${RATE_LIMIT_EXEMPT_LOOPBACK:false}
```

| Env variable | Default | Purpose |
| ------------ | ------- | ------- |
| `RATE_LIMIT_CAPACITY` | 60 | Burst size per client IP (>= 1) |
| `RATE_LIMIT_REFILL` | 20 | Tokens added per interval (>= 1) |
| `RATE_LIMIT_INTERVAL_SECONDS` | 10 | Refill interval in seconds (>= 1) |
| `RATE_LIMIT_EVICTION_SECONDS` | 600 | Idle window after which a bucket is evicted (>= 1) |
| `RATE_LIMIT_MAX_BUCKETS` | 50000 | Hard cap on tracked client buckets (>= 1) |
| `RATE_LIMIT_TRUST_FORWARDED` | false | Honour `X-Forwarded-For` **only** from trusted peers (see §5) |
| `RATE_LIMIT_TRUSTED_PROXIES` | empty | Comma-separated IPs of trusted proxies, exact string match |
| `RATE_LIMIT_EXEMPT_LOOPBACK` | false | Skip limiting for loopback peers (see §9) |

Validation is fail-fast: nonsensical values (e.g. `capacity=0`) abort startup
with a clear message rather than silently disabling the limiter.

---

## 5. Client Identification

The bucket key is the **TCP peer address** by default.

`X-Forwarded-For` is fully client-controlled. Trusting it unconditionally would
let any caller rotate buckets by editing a header and bypass the limit entirely,
so:

* `RATE_LIMIT_TRUST_FORWARDED=false` (default): the header is ignored, and
  rotating `X-Forwarded-For` values cannot defeat the limiter (proven by test).
* `RATE_LIMIT_TRUST_FORWARDED=true` + peer listed in
  `RATE_LIMIT_TRUSTED_PROXIES`: the resolver walks `X-Forwarded-For`
  right-to-left, skipping hops that are themselves trusted proxies, and uses the
  first untrusted address — the closest client the trusted infrastructure
  vouches for.
* Trust flag on but peer not trusted: the header is still ignored.

Notes:

* Trusted proxies are compared as **exact string matches** against the peer's
  IP. List the literal IP your reverse proxy uses to reach the gateway; no CIDR
  support. A safe misconfiguration falls back to peer-address keying (shared
  proxy bucket), never to trusting a spoofable header.
* Forwarded values with ports, quoting, IPv6 brackets and zone indexes
  (`[2001:db8::1]:8080`, `fe80::1%eth0`) are parsed correctly.

The implementation lives in
`gateway/src/main/java/com/ichaabane/gateway/ratelimit/ClientIpResolver.java`.

---

## 6. HTTP Contract

A throttled request receives:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: <seconds until one token is available>
```

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded; retry later"
}
```

* `Retry-After` is a positive integer of seconds, calculated with the same
  monotonic clock as the limiter (ceil of the nanoseconds to the next token).
* The body is fixed and exposes no internal detail (no stack traces,
  no client identity, no limits).
* No `RateLimit-*` / `X-RateLimit-*` headers are emitted today; `Retry-After`
  is the only rate-limit signal (see §13).

---

## 7. Example Scenario

Defaults (capacity 60, refill 20 per 10s):

```text
t=0s    a fresh client has 60 tokens; 60 requests can be made immediately
t=0-10s requests 61+: 429 with Retry-After = seconds to the next token interval
t=10s   bucket refills by 20 (not the full burst — refills accumulate toward
        capacity, never past it)
```

---

## 8. Loopback and Internal Traffic

In the standard deployment every Java service runs on the host and internal
calls go through the gateway over loopback (`http://localhost:8222`). Because
the bucket key is the TCP peer address, **all loopback callers share one
bucket**:

* Local curl / IDE requests and internal service-to-gateway calls compete for
  the same 60-token burst.
* With defaults, both sets of traffic are limited together (this is
  deliberate: exempting loopback would silently disable the limiter for local
  traffic too).

If internal service traffic must not be throttled, set
`RATE_LIMIT_EXEMPT_LOOPBACK=true` — then requests from a loopback peer skip
limiting entirely. Non-loopback traffic is never exempt.

For a real reverse-proxy deployment (or when external clients also traverse the
gateway as loopback peers), prefer the trusted-proxy mode of §5 over an
unconditional loopback exemption.

---

## 9. Testing

Rate-limit tests run per module:

```bash
./gateway/mvnw -f gateway/pom.xml test
```

Relevant classes:

| Test | Scope |
| ---- | ----- |
| `TokenBucketRateLimiterTest` (18 tests) | Burst, refill, rejected-request semantics, per-client isolation, eviction, `max-buckets` cap, concurrency (exactly `capacity` admissions under 16 threads), overflow with long idle, injected deterministic clock |
| `ClientIpResolverTest` (12 tests) | Spoofed header ignored by default, trusted-proxy rightmost-untrusted walk, multi-header XFF, port/quote/IPv6 parsing, loopback opt-in |
| `RateLimitPropertiesTest` (3 tests) | Validation fail-fast, defaults, null normalisation |
| `RateLimitWebFilterTest` (4 tests) | Full gateway context: `/actuator/health` never throttled, 429 + numeric `Retry-After`, clean body, rotating `X-Forwarded-For` cannot bypass |

The limiter unit tests use an injected nanosecond clock, so they are
deterministic and sleep-free. `RateLimitWebFilterTest` boots the real gateway
context with a stubbed JWT decoder to prove the filter runs before
authentication.

---

## 10. Known Limitations

### Per-instance state

The limiter is in-memory and per gateway instance. The current deployment runs
exactly one gateway, so this is correct today. If the gateway is horizontally
scaled, each instance keeps its own buckets and the effective limit becomes
`N x capacity` (N = instances); move to a shared store such as Redis **when,**
and only when, that scaling actually happens.

### Shared loopback bucket

See §8: loopback callers share one bucket in host-based deployments. This can
produce unexpected 429s for internal service traffic mixed with local manual
traffic.

### Single identifier

Limiting keys on client IP only. Authenticated users are not identified by
principal, and there is no per-endpoint or per-token quota.

### No operational metrics

No Micrometer counters (allowed/rejected) or gauges; the limiter is invisible to
monitoring. Rejected requests are not logged either (deliberate, to avoid
write-amplification under a flood), so a 429 storm is only visible to the
clients.

### No rate-limit headers

Only `Retry-After` is returned. `RateLimit-Limit` / `RateLimit-Remaining` /
`RateLimit-Reset` (IETF draft) or legacy `X-RateLimit-*` headers are not
emitted.

---

## 11. Troubleshooting

### Unexpected 429s for a single dev client

1. Check whether internal services share the loopback bucket (§8) — either have
   internal calls avoid the gateway raise `RATE_LIMIT_CAPACITY`, or opt in to
   `RATE_LIMIT_EXEMPT_LOOPBACK`.
2. The burst is 60 with a 10s refill window; tooling that fires more than a few
   requests/second will genuinely throttle.

### 429s behind a reverse proxy

A reverse proxy does not forward its clients' addresses by default, so every
external client arrives with the proxy's IP as the gateway peer and all clients
share one bucket. Configure:

```bash
RATE_LIMIT_TRUST_FORWARDED=true
RATE_LIMIT_TRUSTED_PROXIES=<proxy-ip>
```

…ensuring the proxy sets `X-Forwarded-For` (append mode). Multiple chained
trusted proxies are supported; the last untrusted hop wins.

### My exact `trusted-proxies` entry does not match

Entries are exact string matches against the peer address, no CIDR. Match the
literal IP and checksum its text form.

---

## 12. Production Considerations

1. Set limits through the env variables for the real environment rather than
   relying on dev defaults.
2. Behind load balancers / reverse proxies, enable trusted-proxy mode only for
   infra you control.
3. End TLS at the gateway first; the platform currently has no TLS.
4. Remember the gateway rate-limits while business service ports remain
   directly reachable — closing that surface is still open work (see
   [keycloak.md §10](keycloak.md)).
5. Revisit shared-store (Redis) limiting if you scale the gateway horizontally.
