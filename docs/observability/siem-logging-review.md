# SIEM / Logging Architecture Review — E-Commerce Microservices Platform

**Audit date:** 2026-10-22 / 2026-10-23
**Scope:** config-server, discovery, gateway, customer, product, order, payment, notification
**Reviewed revision:** working tree on `main`, including the uncommitted logging changes.
**Mode:** audit-only; no logging code or configuration was changed.
**Relationship to prior work:** the repository also contains an untracked `log-review.md`
(excluded from git by `.gitignore`). Its structural claims are useful but **stale in places**
(test counts, item R-10 which is now implemented, item R-9's "they agree today" assertion).
Those deltas are called out in §H. This document is the SIEM-focused review requested for the
current tree.

**Verification method:** static review of all eight `logback-spring.xml` files, every
`CorrelationIdFilter`, the gateway logging/security/rate-limit filters, both Kafka producers and
the consumer, all exception handlers, plus a **real runtime capture** of `Logback JsonEncoder`
output and the default text pattern (redacted samples reused below). No SIEM is connected; no
alert is configured; nothing below implies otherwise.

---

## A. Executive summary

**SIEM readiness: LOW–MODERATE — "collection-ready, detection-poor".**

The good parts are real: timestamps, levels, trace/span IDs, thread and logger are emitted
consistently; MDC (including `correlationId` once set) is present in JSON; a JSON profile exists;
security-relevant paths (authentication failure, rate-limit rejection) are logged at `WARN`;
correlation-ID header sanitisation is implemented and tested; log levels are sensible and routine
request completion was deliberately demoted to `DEBUG` to control volume.

What blocks SIEM value:

1. **No identity in any event.** Requests are authenticated at the gateway but no log records
   *who* (`sub`, `client_id`, roles) or *from where* (`source.ip`). Business services never see
   the principal. Attribution is impossible.
2. **No event model.** Messages are free text; there is no `event.name`, no `event.outcome`, no
   schema, and no versioning. Detection rules would parse prose, which is brittle and breaks on
   rewording.
3. **The correlation ID is not reliably present.** The default (text) pattern omits
   `%X{correlationId}` entirely; producers/Feign clients propagate `traceId` under the
   correlation header name; and the Kafka consumer accepts the header unsanitised. Cross-service
   correlation cannot be guaranteed.
4. **JSON output lacks service and environment identity** at top level — the service name exists
   only inside `context.properties.SERVICE_NAME`, and no environment field exists at all.
5. **No timestamp UTC normalisation guarantee** in the text pattern (it uses the JVM default
   offset) and no ISO-8601 field in JSON (epoch millis only).
6. **No forwarding/shipping, rotation or retention.** Services write to stdout redirected to
   `.run/logs/<service>.log` by `start-system.sh`; nothing parses or ships them.
7. **PII is lightly present and not consistently handled** (customer names in `OrderProducer`;
   raw driver/exception text in payment and product handlers; events in Kafka containing e-mail
   addresses).

Net: the platform can be *ingested* into a SIEM today, but it cannot support reliable threat
detection until the identity, event-model and correlation gaps are closed.

---

## B. Field inventory — what is actually emitted

### B.1 Text pattern (default profile), identical in all eight services

```xml
<pattern>%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %5p ${SERVICE_NAME} [%X{traceId:-}] [%X{spanId:-}] --- [%15.15t] %-40.40logger{39} : %m%n%wEx</pattern>
```

| Field | Present | Notes |
|---|---|---|
| timestamp | ✅ | `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` — **JVM-local offset**, not forced UTC. In this host `+01:00`. |
| level | ✅ | Padded `%5p`. |
| service | ✅ | From `spring.application.name` via logback `SERVICE_NAME`. |
| traceId | ✅ | MDC, may be empty. |
| spanId | ✅ | MDC, may be empty. |
| **correlationId** | ❌ | **In MDC but deliberately absent from the pattern.** Only appears if a message interpolates it (gateway does; services mostly do not). |
| thread | ✅ | Truncated/left-padded. |
| logger | ✅ | |
| message | ✅ | Free text with `{}` substitution. |
| exception | ✅ | `%wEx` (full stack + causes). |
| environment | ❌ | No env field. |
| event name / outcome | ❌ | None. |
| source IP / user | ❌ | None. |
| duration | ❌ | Only inside some gateway messages. |

### B.2 JSON profile (`json-logging`), verified from a live capture

Logback `JsonEncoder` produced this field set (values redacted):

```json
{"sequenceNumber":0,"timestamp":1792711807238,"nanoseconds":238667440,
 "level":"INFO","threadName":"http-nio-8071-exec-2",
 "loggerName":"com.ichaabane.ecommerce.order.controller.OrderController",
 "context":{"name":"default","birthdate":1792711778781,
            "properties":{"SERVICE_NAME":"order-service"}},
 "mdc":{"traceId":"6ada9c7f…","spanId":"461bee95…","correlationId":"c24c195a-…"},
 "message":"REST request to create order with reference='{}'",
 "arguments":["REF-INJECT\nINFO fake.security.event user=admin action=approved"],
 "throwable":null}
```

| Field | Present | Notes |
|---|---|---|
| timestamp | ✅ | Epoch **milliseconds** (absolute/UTC) — good for machines, but no ISO-8601 string and no `@timestamp` alias. |
| level | ✅ | String (`INFO`). No numeric `level_value`. |
| service.name | ⚠️ | Only `context.properties.SERVICE_NAME`; not a first-class top-level field. |
| environment | ❌ | Absent. |
| traceId / spanId / correlationId | ✅ | Inside the `mdc` object (not top-level), populated when set. |
| message + arguments | ✅ | `arguments` preserves substituted values — useful, but also means external strings land verbatim in a structured array. |
| exception | ✅ | Structured `throwable` with class/message/stack. Includes full stack traces at `ERROR`/`WARN`. |
| event.name / outcome / duration / source.ip / user.id | ❌ | Absent. |

**Consequence for ingestion:** a SIEM must (a) map `context.properties.SERVICE_NAME` to the
service field, (b) add an environment from the collector (it is not in the payload), (c) parse
`timestamp` as epoch millis, and (d) reach into `mdc` for correlation. None of this is
documented anywhere in the repository.

### B.3 Log levels in use

| Logger | Level | Rationale (from config) |
|---|---|---|
| root | `INFO` (`LOG_LEVEL_ROOT`) | |
| `com.ichaabane` | `INFO` (`LOG_LEVEL_APP`) | |
| gateway rate-limit | `INFO` (`LOG_LEVEL_RATELIMIT`) | class-level comments mention DEBUG for troubleshooting |
| kafka / spring-kafka / mongo / spring-web / security / netty | `WARN` | noise suppression |
| hibernate SQL / bind | `WARN` (`LOG_LEVEL_SQL*`) | |

`org.springframework.security` at `WARN` is acceptable here (the gateway does not log successful
authentication), but it also means any Spring-Security-internal diagnostic is hidden.

---

## C. Correlation and MDC lifecycle

### C.1 HTTP path

* Gateway: `RequestLoggingGlobalFilter` sanitises a client `X-Correlation-Id` against
  `^[A-Za-z0-9._-]{8,64}$`, generates a UUID otherwise, sets it on the downstream request and on
  the response. ✅
* Services: each of customer/product/order/payment has an identical `CorrelationIdFilter`
  (`@Order(HIGHEST_PRECEDENCE)`) that sanitises the header, writes MDC `correlationId`
  (falling back to `traceId` then UUID), also writes `traceId` if absent, and clears
  `correlationId`/`traceId` in `finally`. ✅ for HTTP requests.
* **Gaps.**
  * The text pattern never prints `correlationId` (B.1).
  * `CorrelationIdFilter` may set a synthetic `traceId` *before* Brave's tracing filter runs,
    creating a value that Brave then overwrites — harmless if Brave always wins, confusing if it
    does not. `Needs verification` under the real filter ordering.
  * Header sanitisation is HTTP-only. The Kafka consumer writes an inbound `correlationId`
    **raw** into MDC (`NotificationsConsumer` lines 78–81 and 118–121) — no length limit, no
    control-character rejection (security report F-13).

### C.2 Cross-service (Feign / RestTemplate)

`FeignCorrelationInterceptor` and `ProductClient` set `X-Correlation-Id` from
**`MDC.get("traceId")`**, not from `correlationId`. The gateway's correlation ID is therefore
replaced by the local trace ID on the next hop. `OrderProducer`/`NotificationProducer` do the
same for the Kafka header, while `OrderService` puts `MDC.get("correlationId")` inside the event
payload. This is two different identifiers travelling in parallel; they must not be assumed
equal (the stale `log-review.md` R-9 did assume they agree).

### C.3 Kafka / async path

* Kafka message headers are read defensively (`extractCorrelationId`, String or `byte[]`),
  MDC populated and cleared in `finally` on both listeners. ✅ (This replaced a broken read key.)
* The email `@Async` path is covered by `AsyncMdcConfiguration` / `CorrelationIdTaskDecorator`,
  which snapshots the submitting thread's MDC and restores the previous worker state afterwards.
  ✅ implemented, unit-tested (`AsyncMdcConfigurationTest`). **Not verified end-to-end** through
  the Kafka listener; the previous review recorded empty MDC on the email line, so this needs a
  runtime re-verification before it is claimed as fixed.

---

## D. Coverage vs required security events

| Required event | Logged today? | Where | Quality |
|---|---|---|---|
| Authentication failure | ⚠️ partial | `GatewaySecurityConfig` authentication entry point (`WARN`) | method/path + exception message only; **no source IP, no subject, no reason category** |
| Authorization denial | ❌ n/a | none | no authorization exists (security F-02) |
| Rate-limit rejection | ✅ | `RateLimitWebFilter` (`WARN`) | clientKey + path; no method/route id; `clientKey` may be `unknown`; **`clientKey` is not sanitised for control chars** if a trusted-proxy mode mislabels a hop (low) |
| Validation failure | ⚠️ partial | each service `GlobalExceptionHandler` (`WARN`) | field→message map; no values (good), no endpoint/principal, inconsistent wording per service |
| Unexpected exception | ✅ | `handleGenericException` (`ERROR`) | full stack; can include raw driver/upstream text (F-22) |
| Payment failure | ⚠️ | payment `GlobalExceptionHandler`, producer completion (`ERROR`) | no payment-specific event name; only failure/mismatch paths |
| Kafka processing failure / DLT | ⚠️ partial | Spring Kafka + `KafkaErrorHandlingConfig` | DLT is exercised; the app logs persistence/send failures, but there is **no explicit "message dead-lettered" application event** with topic/partition/offset as structured fields |
| Critical business events | ⚠️ | INFO lines for order created, payment recorded, notification persisted, e-mail sent | free text, no `event.name`/`outcome`, no user attribution |
| Authentication **success** | ❌ | not logged | cannot build an access baseline |
| Config change / admin action | ❌ | none | |
| Service start/stop | ✅ | `ApplicationReadyEvent`/`@PreDestroy` per service | useful for outage detection, but manual lifecycle stop is logged on product/order where only start exists (order has no `@PreDestroy` log) |

---

## E. Privacy review

| Data | Where it appears | Verdict |
|---|---|---|
| Passwords / DB creds | Never in app logs (good) — **but config server serves them over HTTP, security F-01** | Logging clean, transport not |
| Access tokens / Authorization | Not logged (good). Gateway logs the JWT exception message only — Spring messages do not embed the token value | Acceptable, but reason text should be normalised |
| Customer e-mail | Not logged by `EmailService` (good); present in Kafka payloads and Mongo notifications | Event/store exposure, not log exposure |
| Customer first/last name | `OrderProducer.java:21-24` logs them | **Log PII — should be removed** |
| E-mail/identity in exceptions | Possible via raw `DataIntegrityViolationException` (payment) and upstream bodies (`ProductClient`) | Redact |
| Address | Not logged | Good |
| Order reference / amount | Logged extensively (intended) | Acceptable but consider pseudonymising references |
| Trace data | Sampling `1.0`, Zipkin unauthenticated; URLs carry ids | Reduce sampling, protect Zipkin |
| Correlation ID | Sanitised on HTTP, raw on Kafka | Enforce one sanitiser |

Cardinality: no unbounded metric labels were found — the rate-limit counters are intentionally
untagged (`rate.limit.allowed`/`rejected`). Message-based high-cardinality values (order
references, IPs) appear in log *bodies*, which is normal for a SIEM but must be indexed
deliberately.

---

## F. Storage, rotation, retention, forwarding

* Application logging targets **stdout only** (`ConsoleAppender`). No rolling file appender, no
  size/time policy, no retention, no compression.
* `start-system.sh` redirects each service's stdout to `.run/logs/<service>.log` (plain files,
  no rotation) — development-oriented.
* No Filebeat/Fluent Bit/Vector/Logstash config, no syslog target, no OpenTelemetry log export
  (the OTel tracing bridge is present for traces, not logs), no SIEM endpoint.
* Access control on `.run/logs` is filesystem-default only; `security-docs/` is git-ignored but
  the runtime logs are `.gitignore`d too (good, avoids accidental commit).

**Conclusion:** there is no operational log pipeline. Anything fed to a SIEM today would be a
custom collector on stdout.

---

## G. Recommended SIEM event schema

Field names below follow Elastic Common Schema (ECS) conventions where possible so that any of
Splunk/Elastic/OpenSearch/Sentinel can map them; equivalent OCSF mappings are noted in the
remediation plan. `event.dataset` is always `<service>.<category>`.

### G.1 Common envelope (all events)

| Field | Required | Type | Rules |
|---|---|---|---|
| `@timestamp` | ✅ | ISO-8601 UTC `Z` | Force `UTC` in the encoder; never local offset |
| `event.kind` | ✅ | keyword | `event` / `alert` / `metric` |
| `event.dataset` | ✅ | keyword | e.g. `order.business`, `gateway.security` |
| `event.name` | ✅ | keyword | stable, lower-dot, e.g. `auth.failure`, `payment.failure` |
| `event.outcome` | ✅ | keyword | `success` / `failure` / `unknown` |
| `event.severity` | ✅ | keyword | `info`/`warn`/`error`/`critical` (map from log level) |
| `log.level` | ✅ | keyword | raw Logback level |
| `service.name` | ✅ | keyword | first-class, not nested |
| `service.version` | ✅ | keyword | build version / git SHA |
| `deployment.environment` | ✅ | keyword | `dev`/`staging`/`prod` |
| `trace.id`, `span.id` | optional | keyword | from Micrometer |
| `correlation.id` | ✅ where any hop exists | keyword | one canonical value across HTTP+Kafka |
| `http.request.method`, `url.path` | conditional | keyword | path must be **templated** (`/orders/{id}`), never raw with identifiers |
| `http.response.status_code` | conditional | long | |
| `source.ip` | ✅ for edge/security events | ip | sanitised, from the trusted-proxy resolver |
| `user.id`, `user.roles`, `client.id` | ✅ for security/business events | keyword | from JWT `sub`/`roles`/`azp`; never from request body |
| `error.type`, `error.message`, `error.stack_trace` | conditional | keyword | message sanitised+truncated; stack only for 5xx/unexpected |
| `duration.ms` | optional | long | request/handler duration |
| `process.thread.name`, `log.logger` | optional | keyword | |

### G.2 Redaction rules (apply at emit time, not in the SIEM)

* Never emit: passwords, secrets, API keys, `Authorization`/`Cookie` values, full DB URLs with
  credentials, SMTP credentials.
* Truncate every externally supplied string to a documented max (e.g. 512 chars) and strip
  CR/LF and C0/C1 control characters before it becomes a field or a message argument
  (this covers the Kafka correlation header and business fields — security F-13).
* Do not emit customer e-mail or name: emit `user.id` (pseudonymous) and, if an address is
  indispensable, a keyed hash `email.hash`. Never derive the recipient from a request body.
* `url.path` must be the route template; reject/redact raw path variables and query strings in
  security events.
* `source.ip` only from the resolver that honours trusted proxies (never raw `X-Forwarded-For`).

### G.3 Example normalised events

**1. Authentication failure**
```json
{"@timestamp":"2026-10-23T00:12:31.442Z","event":{"kind":"event","dataset":"gateway.security",
 "name":"auth.failure","outcome":"failure","severity":"warn"},
 "service":{"name":"gateway-service","version":"0.0.1"},
 "deployment":{"environment":"prod"},
 "http":{"request":{"method":"GET"},"response":{"status_code":401}},
 "url":{"path":"/api/v1/orders"},"source":{"ip":"203.0.113.7"},
 "error":{"type":"invalid_token"},
 "trace":{"id":"6ada9c7f…"},"correlation":{"id":"c24c195a-…"}}
```
*Required:* timestamp, event name/outcome, service, env, method, templated path, source.ip,
error.type (`missing_token`/`invalid_token`/`expired_token`/`wrong_issuer`/`wrong_audience`).
*Optional:* `client.id`, `user.id` (usually absent), `http.version`, `duration.ms`.
*Detection purpose:* credential stuffing, token replay, misconfigured clients.
*Suggested rule:* `auth.failure` count per `source.ip` > 20 in 5 min, or > 100 globally in 5 min,
or any `wrong_audience`/`wrong_issuer` (configuration attack indicator).
*False positives:* clock skew, rotating proxies/NAT, a broken deploy, legitimate password typos.

**2. Authorization denial**
```json
{"@timestamp":"…Z","event":{"dataset":"order.security","name":"authz.denied",
 "outcome":"failure","severity":"warn"},
 "service":{"name":"order-service"},"deployment":{"environment":"prod"},
 "user":{"id":"u-123","roles":["CUSTOMER"]},
 "http":{"request":{"method":"GET"},"response":{"status_code":403}},
 "url":{"path":"/api/v1/orders/{id}"},"resource":{"type":"order","id":"42"},
 "trace":{"id":"…"},"correlation":{"id":"…"}}
```
*Required:* user.id, roles, method, templated path, resource id (non-enumerable), status.
*Detection:* privilege-escalation attempts, IDOR probing.
*Rule:* > 5 denials / user / 10 min, or one user hitting > 10 distinct resource ids.
*FP:* legitimate role transitions, stale client caches during rollout.

**3. Rate-limit rejection**
```json
{"@timestamp":"…Z","event":{"dataset":"gateway.security","name":"rate_limit.rejected",
 "outcome":"failure","severity":"warn"},
 "service":{"name":"gateway-service"},"deployment":{"environment":"prod"},
 "source":{"ip":"203.0.113.7"},
 "http":{"request":{"method":"POST"}},"url":{"path":"/api/v1/orders"},
 "rule":{"id":"edge.token_bucket","limit":{"capacity":60,"refill":20,"interval_s":10}},
 "retry_after_s":7,"correlation":{"id":"…"}}
```
*Required:* source.ip, method, templated path, rule id/limits, retry_after.
*Detection:* volumetric abuse, credential-stuffing bursts, scraping.
*Rule:* > 100 rejections / source.ip / 5 min, or total rejections > 5× baseline.
*FP:* NAT/shared egress IPs, legitimate launches, the internal loopback bucket sharing bug
(security F-15) which can 429 genuine checkout traffic — flag this specifically.

**4. Input-validation failure**
```json
{"@timestamp":"…Z","event":{"dataset":"order.security","name":"validation.failure",
 "outcome":"failure","severity":"warn"},
 "service":{"name":"order-service"},"deployment":{"environment":"prod"},
 "http":{"request":{"method":"POST"},"response":{"status_code":400}},
 "url":{"path":"/api/v1/orders"},"user":{"id":"u-123"},
 "validation":{"fields":["reference","products[2].quantity"],"error_codes":["NotBlank","Positive"]},
 "correlation":{"id":"…"}}
```
*Required:* fields and error codes only — **never the rejected values**.
*Detection:* fuzzing/injection probing (repeated pattern/boundary violations), API misuse.
*Rule:* validation failures per user/ip > 3× the 7-day baseline, or spikes in
`error_code` distribution.
*FP:* client version bugs; a single buggy integration can look like an attack.

**5. Payment failure**
```json
{"@timestamp":"…Z","event":{"dataset":"payment.business","name":"payment.failure",
 "outcome":"failure","severity":"error"},
 "service":{"name":"payment-service"},"deployment":{"environment":"prod"},
 "user":{"id":"u-123"},"order":{"reference":"ord-hash-9f2c","id":42},
 "payment":{"method":"CREDIT_CARD","amount":129.90,"currency":"EUR","failure_reason":"psp_timeout"},
 "trace":{"id":"…"},"correlation":{"id":"…"}}
```
*Required:* order reference (pseudonymised), amount, method, `failure_reason` from a closed
enum. *Never* card data. For this codebase, also emit `payment.duplicate` when the idempotency
fast-path returns an existing id with **different** amount/customer (security F-03).
*Detection:* fraud, PSP incidents, duplicate-charge attempts.
*Rule:* payment failure rate > 5 % per 5 min; any `payment.duplicate_mismatch`; amount
anomalies by user/order.
*FP:* PSP maintenance windows, test traffic.

**6. Kafka processing failure / dead-letter**
```json
{"@timestamp":"…Z","event":{"dataset":"notification.pipeline","name":"kafka.message.dead_lettered",
 "outcome":"failure","severity":"error"},
 "service":{"name":"notification-service"},"deployment":{"environment":"prod"},
 "messaging":{"system":"kafka","destination":"order-topic","partition":0,"offset":8,
              "consumer_group":"orderGroup"},
 "event":{"reason":"persistence_failure"},"error":{"type":"NullPointerException"},
 "trace":{"id":"…"},"correlation":{"id":"…"}}
```
*Required:* topic, partition, offset, consumer group, reason, exception type.
*Detection:* poison messages, schema drift, downstream DB outages.
*Rule:* any DLT record (single event alert) or DLT rate > 0 sustained for 5 min.
*FP:* deliberate poison-message tests; a broker rebalance storm.

**7. Unexpected application exception**
```json
{"@timestamp":"…Z","event":{"dataset":"order.app","name":"app.unexpected_exception",
 "outcome":"failure","severity":"error"},
 "service":{"name":"order-service","version":"0.0.1"},"deployment":{"environment":"prod"},
 "http":{"request":{"method":"POST"},"response":{"status_code":500}},
 "url":{"path":"/api/v1/orders"},"user":{"id":"u-123"},
 "error":{"type":"DataIntegrityViolationException",
          "message":"redacted-constraint-summary","stack_hash":"a91f…"},
 "trace":{"id":"…"},"correlation":{"id":"…"}}
```
*Required:* exception class, redacted message, `stack_hash` (grouping), endpoint, status.
*Optional:* stack trace only in a separate, access-controlled dataset.
*Detection:* error-rate anomalies, release regressions, dependency failures.
*Rule:* 5xx rate > 1 % (or > 2× baseline) over 5 min, or a new `stack_hash` appearing.
*FP:* planned deploys, load tests, a single tenant's bad data.

### G.4 Additional events worth emitting (not yet implemented)

`auth.success` (with `user.id`, `client.id`, `source.ip`) — baseline/UEBA;
`authz.granted` for sensitive operations; `order.created` / `order.failed`;
`payment.recorded`; `notification.email.sent` / `.failed`; `config.changed`;
`service.started` / `service.stopped`; `migration.applied` (Flyway).

---

## H. Gap analysis vs the previous (untracked) review and current tree

| Item from `log-review.md` | Current tree | Action |
|---|---|---|
| S-1 PII in `DuplicateKeyException` | Fixed (generic message) | keep |
| S-2 correlation sanitisation | Fixed on HTTP, **not on Kafka** | extend sanitiser to the consumer (F-13) |
| S-3 RestTemplate timeouts | Present for `RestTemplate`; **absent for Feign** | add Feign timeouts (F-15) |
| S-4 Kafka correlation MDC | Defensive extraction present | keep |
| R-10 async e-mail MDC | `AsyncMdcConfiguration` **now exists** (claim is stale) | verify end-to-end, then update the review |
| R-9 two propagation strategies | **Still divergent and worse than claimed** — producers/Feign use `traceId`, payload uses `correlationId` | unify (F-21) |
| test counts / "all pass" | 474 run, notification red | superseded by test report |
| "A− production-ready logging" grade | Not supported: identity, event model, UTC, service/env fields, shipping and retention are all missing | re-grade in this document |

---

## I. Recommended detections (high value, buildable)

1. **Repeated authentication failures** — per `source.ip` and globally; separate rules for
   `expired_token` (benign drift) vs `invalid_token` (attack).
2. **Repeated authorization denials** — per `user.id`; escalate on distinct-resource breadth
   (IDOR probing).
3. **Injection attempts** — validation-failure anomalies: boundary/pattern violations, control
   characters in fields, LIKE-metacharacter floods on `/products/search`, and any
   `MethodArgumentTypeMismatch` burst. Also alert on the presence of CR/LF in a would-be log
   field (log-forging attempt) once sanitisation emits `validation.control_char_rejected`.
4. **Rate-limit abuse** — per IP and per route; explicitly distinguish the internal loopback
   bucket so genuine checkout 429s are not masked.
5. **Unusual payment activity** — failure-rate spikes, amount outliers, `payment.duplicate`
   with mismatched amount/customer, payments created from non-service identities.
6. **Service outage / degradation** — absence of heartbeat (`service.started` plus periodic
   health) and rises in timeout/connection-refused errors; correlate with
   `service.stopped`/`@PreDestroy`.
7. **Abnormal error-rate spikes** — 5xx per service/route, new `stack_hash`, DLT growth.
8. **Kafka pipeline integrity** — any DLT record; consumer lag (needs broker metrics, not logs);
   duplicate-event detection once an event id exists.
9. **Correlation gap** — alert if a security event arrives without `correlation.id` (indicates a
   propagation regression).
10. **Config/secret exposure** — alert on any log line containing `password=`, `secret=`,
    `Authorization:` (should be zero) and on config-server access to `*-service/default` from
    unexpected sources (security F-01).

Each rule must specify its false-positive story before enablement; the examples in §G give the
starting point.

---

## J. Prioritised logging/SIEM remediation

| # | Action | Why | Effort |
|---|---|---|---|
| 1 | Emit a structured event envelope (`event.name`/`outcome`/`severity`) from a shared helper; convert the security-relevant call sites first (auth failure, rate-limit, validation, payment, Kafka, unexpected error) | enables every detection | M |
| 2 | Capture and log identity: subject, `azp`/client, roles, sanitised `source.ip` at the gateway **and** forward identity to services | attribution is impossible today | M |
| 3 | Force UTC ISO-8601 timestamps; add first-class `service.name`, `service.version`, `deployment.environment` | correct correlation/time handling | S |
| 4 | Unify one correlation id; add `correlationId` to the text pattern; sanitise the Kafka header; propagate the same value in Feign/RestTemplate/producers | end-to-end tracing | S–M |
| 5 | Add an explicit application-level "message dead-lettered" event with topic/partition/offset/group | reliable DLT detection | S |
| 6 | Remove PII from logs (`OrderProducer` names); redact raw driver/upstream exception text | privacy | S |
| 7 | Ship logs (Vector/Fluent Bit/OTel collector) with rotation/retention and access control | no pipeline exists | M |
| 8 | Lower trace sampling in production and protect Zipkin | PII/cost | S |
| 9 | Add log-schema tests (field presence) and a CI lint rejecting forbidden keys (`password`, `authorization`, `email`, raw values in validation events) | prevent regression | M |

---

## K. Statement of limits

No SIEM, log shipper, or alert rule is configured in this repository, and none was created by
this audit. The field inventory was verified against a real `Logback JsonEncoder` capture; the
detection rules in §I are recommendations, not active controls. Absence of a log-derived finding
must not be read as absence of the underlying activity — it currently often means the platform
did not emit the event at all.
