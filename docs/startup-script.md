# Startup Script Guide

> Guides the startup of the entire **E-Commerce Backend Platform**. See
> `start-system.sh` at the repository root — this document explains *why* the
> system is started the way it is and how to use the script safely.

---

## 1. What the script does

`start-system.sh` brings the whole platform up in the correct order with **no
hard-coded sleeps for readiness**. It:

1. Checks the machine (tools, Docker daemon, JDK, wrapper scripts).
2. Starts the Docker infrastructure (PostgreSQL, MongoDB, Kafka + ZooKeeper,
   MailDev) and waits until each container is actually ready.
3. Creates the PostgreSQL databases that every JDBC service needs on a fresh
   data volume (`order`, `payment`, `product`).
4. Starts `config-server` → waits for it to be healthy.
5. Starts `discovery` (Eureka) → waits for it to be healthy.
6. Starts the 6 business services **in parallel** (gateway, customer,
   product, payment, order, notification).
7. Polls every service with its **own health endpoint** until it is ready.
8. Keeps running in the foreground; on `Ctrl+C` (or `SIGTERM`/`SIGHUP`) it
   stops every child process cleanly.

Service ports and health endpoints (all configured in `docker-compose.yml` /
`src/main/resources/application.yml`):

| Service | Port | Health check |
|---|---|---|
| config-server | 8888 | `GET /actuator/health` |
| discovery (Eureka) | 8761 | `GET /eureka/apps` |
| gateway | 8222 | `GET /actuator/health` |
| customer | 8090 | `GET /actuator/health` |
| product | 8050 | `GET /actuator/health` |
| payment | 8060 | `GET /actuator/health` |
| order | 8070 | `GET /actuator/health` |
| notification | 8040 | `GET /actuator/health` |

> Why `/actuator/health`? Because all services have the Spring Boot Actuator
> dependency. The Eureka server intentionally does **not**, so it is polled on
> `/eureka/apps`. While a backing datastore (Postgres, MongoDB, Kafka, SMTP) is
> down, `/actuator/health` returns HTTP **503** — the script therefore waits
> for real availability, not just for the port to open.

## 2. Why the script is needed

This is a true microservices system: 8 services plus 4 backing infrastructure
components, wired together by:

* **Config Server** – every service imports `optional:configserver:…` (its
  port, datasource, Kafka and mail settings live in Config Server, not in
  Java code).
* **Eureka** – services register themselves; the gateway and OpenFeign clients
  resolve endpoints by name (`lb://CUSTOMER-SERVICE`).
* **Kafka** – the checkout flow is async: `order-service` and `payment-service`
  publish to `order-topic` / `payment-topic`; `notification-service` consumes
  both.
* **Per-service databases** – a database must be reachable *before* the
  service is considered ready.

A naive `start-all.sh` that launches all 8 Java services at once fails
repeatedly: if config-server or Eureka is down, every service crashes on
startup; if a datastore is down, the health endpoint (and traffic) stays red
until it is up. This script starts **prerequisites first, waits for them, and
only then starts dependents**.

## 3. Microservices architecture and startup dependencies

```
docker-compose.yml (infrastructure only)
  │  postgres · mongo · kafka/zookeeper · maildev
  ▼
config-server :8888  ◄── every business service imports its config from here
  ▼
discovery :8761      ◄── gateway + business services register here
  ▼
gateway :8222 ┬──► customer :8090
  │           ├──► product :8050
  │           ├──► order :8070
  │           ├──► payment :8060
  │           └──► notification :8040
```

Starting prerequisites first is required because of **Spring Cloud Config**:
business services boot with `spring.config.import: optional:configserver:…`.
`optional:` only *prevents a startup crash* — it does **not** provide the
configuration. The services would start with default values (no port, no
database), so the config-server must be healthy first. The same applies to
Eureka: gateway and clients need the registry to resolve `lb://…` destinations
at request time, and order/payment/notification need Kafka and databases.

## 4. Startup order (dependency graph)

| Phase | Services | Why |
|---|---|---|
| 1 | `docker compose up -d` | Postgres, Mongo, Kafka/ZooKeeper, MailDev (compose `depends_on` handles internal ordering) |
| 2 | Infrastructure readiness checks for `ms_pg_sql`, `mongo_db`, `ms_kafka`, `ms-mail-dev` | Containers must accept connections before any Java service can be healthy (e.g. Postgres must accept the `order`/`payment`/`product` DBs) |
| 3 | `config-server` | Every service imports its configuration from it |
| 4 | `discovery` (Eureka) | Clients register with and look up the registry |
| 5 | `gateway` + `customer` + `product` + `payment` + `order` + `notification` | Only depends on phases 3–4 |

## 5. What can run in parallel

The **6 business services** start in parallel — none of them blocks another at
startup:

* `gateway`, `customer`, `product`, `payment`, `order`, `notification`

This is safe because the only inter-service communication at startup would be
Kafka consumer group joins (`notification`), which are lazy. The single
sequential chain is: **infrastructure → config-server → discovery → wave of
business services**.

## 6. What must wait for other services

| Service | Waits for |
|---|---|
| `config-server` | infrastructure (n/a — nothing) |
| `discovery` | `config-server` (its `application.yml` imports config from it) |
| gateway, customer, product, payment, order, notification | `config-server` and `discovery` (config + registry) |
| `order`, `payment`, `notification` | infrastructure (Postgres/Kafka/Mongo/SMTP) |

## 7. How readiness is detected

* **Infrastructure containers** – TCP/command probes: `pg_isready` for
  Postgres, `mongosh` ping for MongoDB, TCP 9092 for Kafka, TCP 1025 for
  MailDev (`~/.mvn/...` no — see script).
* **Spring services** – HTTP `GET /actuator/health` (`-f` semantics: 2xx is
  healthy; 503 while a dependency is down). The script **fails a service** if
  it does not become healthy within the configured timeout. This is a real
  availability gate, not a timer.
* **Eureka** – `GET /eureka/apps` (no Actuator dependency).

## 8. How failures are handled

* **Tool/Docker/JDK problems** – `preflight()` aborts with a clear message.
* **Container not ready after `INFRA_TIMEOUT` (120s)** – dumps `docker logs`
  for that container and exits non-zero.
* **Service process dies before it becomes healthy** – immediate failure,
  prints the last `LOG_TAIL_LINES` (40) lines of the service log, exits
  non-zero.
* **Service unhealthy after `SERVICE_TIMEOUT` (300s default)** – same
  failure path with a timeout message.
* **Port already occupied** – refuses to start, prints the offender and how to
  kill it (`ss -ltnp | grep :PORT`).
* **Idempotent start** – a service whose health endpoint already answers is
  reported as "already healthy — skipping"; a missing/wrong JDK is auto-detected
  (see prerequisites).
* **Graceful shutdown** – `Ctrl+C` / `SIGTERM` / `SIGHUP` → `cleanup()` sends
  `SIGTERM` to each service's whole process group (via the PID file created by
  `setsid`), waits up to 10s, then `SIGKILL`s survivors. Zero orphaned
  `mvn`/`java` processes were observed in tests.

## 9. How to start the entire system

```bash
# 1) Optional but recommended: build everything first (see also README.md)
./start-system.sh start --infra-only     # start only the docker infrastructure
```

Then, in a terminal, run the script (it stays in the foreground):

```bash
./start-system.sh start
```

Expected output: a phase-by-phase log ending in a table like

```text
==== System is UP ====
  SERVICE            PORT   STATUS      LOG
  config-server      8888   healthy     .run/logs/config-server.log
  discovery          8761   healthy     .run/logs/discovery.log
  gateway            8222   healthy     .run/logs/gateway.log
  customer           8090   healthy     .run/logs/customer.log
  product            8050   healthy     .run/logs/product.log
  payment            8060   healthy     .run/logs/payment.log
  order              8070   healthy     .run/logs/order.log
  notification       8040   healthy     .run/logs/notification.log
```

The script then blocks until `Ctrl+C` is pressed (or until containers stop).

## 10. How to stop the system

```bash
# From the terminal running the script: Ctrl+C (or kill -TERM <script-pid>)
```

`stop` is available for the case where the script is no longer running:

```bash
./start-system.sh stop             # stops all 8 services (uses PID files)
./start-system.sh stop --infra     # also stops the docker containers
```

> Ubuntu/Debian `systemd` note: the script is a dev-oriented orchestrator, not
> a service manager. For production, run it under a process supervisor
> (systemd, Supervisor, PM2…). See `docs/deployment.md`.

## 11. Troubleshooting common startup problems

| Symptom | Likely cause & fix |
|---|---|
| `preflight: docker daemon is not reachable` | Start Docker Desktop / the Docker daemon first |
| `no JDK 17/21 found … falling back to PATH java` | The machine has Java ≥ 22 (Lombok 1.18.30 cannot compile on it). Install a JDK 17/21 and export `JAVA_HOME`, or the script auto-detects one in `~/.jdks`, `~/.sdkman` or `/usr/lib/jvm`. |
| `port 8761 is occupied by another process` | Something already runs on Eureka's port; stop it, or reuse it (`--skip-infra` + change port) |
| Infrastructure containers restart constantly (OOMKilled / crash-loop) | See `docs/deployment.md` Troubleshooting; the script reports `docker logs` on timeout |
| A service status is `DOWN` in `status` | `./start-system.sh logs <service>` shows the latest log; log files live in `.run/logs/<service>.log` |
| `Config Server connection refused` at startup | The script starts config-server first — if using the script nothing to do; manual run: start config-server before anything else |
| Eureka console empty | Wait 30–60s; the script waits properly (config + discovery phases) |
| Kafka consumer not receiving messages | Compose starts Kafka on `localhost:9092`; consumers connect to `localhost:9092` |
| MailDev not receiving email | Notification config already points to `localhost:1025` (MailDev) |

## 12. Prerequisites and environment variables

### Prerequisites

| Tool | Required for |
|---|---|
| Bash ≥ 4 | `bash -n start-system.sh` |
| Docker + `docker compose` plugin | Infrastructure containers |
| JDK 17 or 21 (`java`/`mvn` on PATH, or `JAVA_HOME`) | Compiling & running the 8 services |
| `curl`, `sed`, `awk`, `grep` | Readiness probes |
| `flock` (optional) | Concurrent-start guard (missing → ignored) |

### Environment variables

All are optional with sensible defaults; the script reads them from the shell
that launches it:

| Variable | Default | Meaning |
|---|---|---|
| `LOG_DIR` | `.run/logs` | Where per-service log files are stored |
| `PID_DIR` | `.run/pids` | Where PID files and the lock file live |
| `INFRA_TIMEOUT` | `120` | Seconds to wait for each infrastructure container |
| `CONFIG_TIMEOUT` | `180` | Seconds to wait for config-server |
| `DISCOVERY_TIMEOUT` | `180` | Seconds to wait for Eureka |
| `SERVICE_TIMEOUT` | `300` | Seconds to wait for each business service |
| `POLL_INTERVAL` | `2` | Health-poll interval in seconds |
| `LOG_TAIL_LINES` | `40` | Log lines dumped on failure |
| `PG_USER` | `ichaabane` | Postgres user used for the DB check / creation |
| `JAVA_HOME` | (auto-detected) | JDK 17/21; if unset the script looks for one automatically |

> Note: `JAVA_HOME` is only trusted if it points at a **JDK 17 or 21**; any
> other value is ignored and a compatible JDK is auto-selected. This keeps you
> from accidentally running the Lombok-compiled services on a Java 22+ JRE.

## 13. Common commands

```bash
./start-system.sh start                  # full system
./start-system.sh start --skip-infra     # reuse existing containers
./start-system.sh start --infra-only     # infrastructure only
./start-system.sh stop                   # services only
./start-system.sh stop --infra           # services + containers
./start-system.sh status                 # health table
./start-system.sh logs order             # follow one log
./start-system.sh help                   # all options
```

### Full stop (when containers are not needed anymore)

```bash
./start-system.sh stop --infra
docker compose down -v   # DESTRUCTIVE: removes all data volumes
```

Lint/verify the script without starting anything:

```bash
bash -n start-system.sh    # syntax check
```

---

## Links

* [Architecture](docs/architecture.md) · [Databases](docs/database.md) ·
  [Testing](docs/tests.md) · [API](docs/api.md) · [Deployment](docs/deployment.md) ·
  [Main README](../README.md)
