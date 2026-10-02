#!/usr/bin/env bash
# =============================================================================
# start-system.sh — Bootstrap the e-commerce microservices platform
#
# Starts the full system in the correct dependency order:
#
#   [docker-compose infra]  ->  config-server (:8888)  ->  discovery (:8761)
#        ->  gateway, customer, product, payment, order, notification (parallel)
#
# Readiness is detected with real health probes (no fixed sleeps):
#   * every Spring service except the Eureka server ships Actuator, and
#     /actuator/health returns HTTP 503 until its backing datastore is reachable
#   * the Eureka server (no Actuator dependency) is polled on /eureka/apps
#   * infrastructure readiness is polled with pg_isready / mongosh / TCP probes
#
# Subcommands:
#   start [--skip-infra | --infra-only]   start the whole system (default)
#   stop  [--infra]                       stop services (and optionally infra)
#   status                                show a health table
#   logs <service>                        follow a service log
#   help                                  this help
#
# While `start` runs in the foreground, Ctrl+C stops every child process.
# Logs: .run/logs/<service>.log      PID files: .run/pids/<service>.pid
# =============================================================================
set -Eeuo pipefail

# ----------------------------------------------------------------------------
# Configuration (overridable via environment)
# ----------------------------------------------------------------------------
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
LOG_DIR="${LOG_DIR:-$ROOT_DIR/.run/logs}"
PID_DIR="${PID_DIR:-$ROOT_DIR/.run/pids}"

INFRA_TIMEOUT="${INFRA_TIMEOUT:-120}"     # seconds for containers to become ready
CONFIG_TIMEOUT="${CONFIG_TIMEOUT:-180}"   # seconds for config-server
DISCOVERY_TIMEOUT="${DISCOVERY_TIMEOUT:-180}"  # seconds for Eureka
SERVICE_TIMEOUT="${SERVICE_TIMEOUT:-300}" # seconds per business service
POLL_INTERVAL="${POLL_INTERVAL:-2}"       # health-poll interval
LOG_TAIL_LINES="${LOG_TAIL_LINES:-40}"    # log lines dumped on failure

# service|working-dir|port|health-path
# wave 1 = sequential prerequisites, wave 2 = parallel services
SERVICES=(
  "config-server|config-server|8888|/actuator/health"
  "discovery|discovery|8761|/eureka/apps"
  "gateway|gateway|8222|/actuator/health"
  "customer|customer|8090|/actuator/health"
  "product|product|8050|/actuator/health"
  "payment|payment|8060|/actuator/health"
  "order|order|8070|/actuator/health"
  "notification|notification|8040|/actuator/health"
)
# Runtime-only callers; start them last within the parallel wave.
WAVE2_ORDER=(gateway customer product payment order notification)

INFRA_CONTAINERS=(ms_pg_sql mongo_db ms_kafka ms-mail-dev)
PG_USER="${PG_USER:-ichaabane}"
PG_DATABASES=(order payment product)   # JDBC URLs assume these exist; Postgres does not create them

# ----------------------------------------------------------------------------
# Pretty logging
# ----------------------------------------------------------------------------
if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'
  C_RED=$'\033[31m'; C_CYAN=$'\033[36m'; C_DIM=$'\033[2m'
else
  C_RESET=""; C_GREEN=""; C_YELLOW=""; C_RED=""; C_CYAN=""; C_DIM=""
fi
log()      { printf '%s %s\n' "$1" "${2:-}"; }
log_info() { log "${C_CYAN}[INFO ]${C_RESET}" "$1"; }
log_ok()   { log "${C_GREEN}[ OK  ]${C_RESET}" "$1"; }
log_warn() { log "${C_YELLOW}[WARN ]${C_RESET}" "$1"; }
log_err()  { log "${C_RED}[ERROR]${C_RESET}" "$1" >&2; }
log_step() { log "" ""; log "${C_CYAN}==> $1${C_RESET}" ""; }

# ----------------------------------------------------------------------------
# Low-level checks
# ----------------------------------------------------------------------------
svc_field() { # svc_field <name> <field-index 1..4>
  local entry
  for entry in "${SERVICES[@]}"; do
    if [[ "${entry%%|*}" == "$1" ]]; then
      echo "$entry" | cut -d'|' -f"$2"
      return 0
    fi
  done
  return 1
}
svc_dir()  { svc_field "$1" 2; }
svc_port() { svc_field "$1" 3; }
svc_path() { svc_field "$1" 4; }
svc_pid()  { [[ -f "$PID_DIR/$1.pid" ]] && cat "$PID_DIR/$1.pid" 2>/dev/null || true; }

port_open() { # TCP port probe using the bash built-in
  timeout 2 bash -c ": >/dev/tcp/127.0.0.1/$1" 2>/dev/null
}

http_healthy() { # true if the service health URL answers 2xx
  curl -fsS -o /dev/null -m 3 "http://localhost:$(svc_port "$1")$(svc_path "$1")" 2>/dev/null
}

container_running() {
  docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null | grep -q true
}

pg_ready() {
  docker exec ms_pg_sql pg_isready -U "$PG_USER" -q 2>/dev/null || port_open 5432
}
mongo_ready() {
  docker exec mongo_db mongosh --quiet --eval 'db.runCommand({ping: 1}).ok' 2>/dev/null | grep -q 1 \
    || port_open 27017
}
kafka_ready()  { port_open 9092; }
mail_ready()   { port_open 1025; }

ensure_pg_database() { # idempotent CREATE DATABASE for order/payment/product
  local db exists
  for db in "${PG_DATABASES[@]}"; do
    exists="$(docker exec ms_pg_sql psql -U "$PG_USER" -d postgres -tAc \
      "SELECT 1 FROM pg_database WHERE datname='$db'" 2>/dev/null || true)"
    if [[ "$exists" != "1" ]]; then
      log_info "postgres: creating missing database '$db'"
      # "order" is a reserved word in SQL, hence the double quotes
      docker exec ms_pg_sql psql -U "$PG_USER" -d postgres -c "CREATE DATABASE \"$db\"" >/dev/null
      log_ok "postgres: database '$db' created"
    fi
  done
}

# ----------------------------------------------------------------------------
# Process management
# ----------------------------------------------------------------------------
jdk_major() { # major version of the JDK at $1 (17, 21, ...), from its release file
  grep -s '^JAVA_VERSION=' "$1/release" | cut -d'"' -f2 | cut -d. -f1
}

find_suitable_jdk() { # echo path of the first JDK 17/21 found, or fail
  local jdk ver
  for jdk in "$HOME"/.jdks/*/ "$HOME"/.sdkman/candidates/java/*/ /usr/lib/jvm/*/; do
    [[ -x "${jdk}bin/java" ]] || continue
    ver="$(jdk_major "${jdk%/}")"
    if [[ "$ver" == 17 || "$ver" == 21 ]]; then
      echo "${jdk%/}"
      return 0
    fi
  done
  return 1
}

ensure_java() { # Spring Boot 3.2 + Lombok 1.18.30 need JDK 17 or 21, not 22+
  local jdk_home
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] \
      && [[ "$(jdk_major "$JAVA_HOME")" == 17 || "$(jdk_major "$JAVA_HOME")" == 21 ]]; then
    log_ok "using JAVA_HOME=$JAVA_HOME (JDK $(jdk_major "$JAVA_HOME"))"
    return 0
  fi
  [[ -n "${JAVA_HOME:-}" ]] \
    && log_warn "JAVA_HOME=${JAVA_HOME:-<unset>} is not a JDK 17/21 — searching for a compatible JDK"
  if jdk_home="$(find_suitable_jdk)"; then
    export JAVA_HOME="$jdk_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    log_info "auto-selected JDK: $JAVA_HOME"
    return 0
  fi
  log_warn "no JDK 17/21 found (~/.jdks, ~/.sdkman, /usr/lib/jvm) — falling back to PATH java"
  log_warn "note: Lombok 1.18.30 fails on JDK 22+; point JAVA_HOME at a JDK 17/21 if builds fail"
}

cleanup_flag=""
cleanup() {
  [[ -n "$cleanup_flag" ]] && return 0
  cleanup_flag=1
  local pid name had_pids=0
  for name in "${WAVE2_ORDER[@]}" discovery config-server; do
    pid="$(svc_pid "$name")"
    [[ -z "$pid" ]] && continue
    had_pids=1
    log_info "stopping $name (pid $pid)..."
    # mvnw runs with its own process group (setsid): signal the whole group so
    # the forked Spring Boot JVM cannot be orphaned; fall back to the PID.
    kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
  done
  (( had_pids )) || return 0
  # Grace period, then force-kill survivors.
  local waited=0
  while (( waited < 10 )); do
    local alive=0
    for name in "${WAVE2_ORDER[@]}" discovery config-server; do
      pid="$(svc_pid "$name")"
      [[ -n "$pid" ]] && kill -0 -- "-$pid" 2>/dev/null && alive=1
    done
    (( alive )) || break
    sleep 1; waited=$((waited + 1))
  done
  for name in "${WAVE2_ORDER[@]}" discovery config-server; do
    pid="$(svc_pid "$name")"
    [[ -z "$pid" ]] && continue
    kill -KILL -- "-$pid" 2>/dev/null || true
    rm -f "$PID_DIR/$name.pid"
  done
  log_ok "all services stopped"
}

on_interrupt() {
  printf '\n'
  log_warn "Ctrl+C received — shutting down..."
  cleanup
  exit 130
}

dump_log_tail() { # on failure, show the last lines of the service log
  local name="$1" log_file="$LOG_DIR/$1.log"
  log_err "last $LOG_TAIL_LINES lines of $log_file:"
  tail -n "$LOG_TAIL_LINES" "$log_file" 2>/dev/null | sed 's/^/    /' >&2 || true
}

start_service() { # launch one service in the background; does NOT wait
  local name="$1" dir port
  dir="$(svc_dir "$name")"; port="$(svc_port "$name")"

  if http_healthy "$name"; then
    log_ok "$name is already healthy on :$port — skipping (idempotent)"
    return 0
  fi
  if port_open "$port"; then
    log_err "$name: port $port is occupied by another process but is not healthy."
    log_err "Stop that process (or the previous instance) and retry, e.g.: ss -ltnp | grep :$port"
    return 1
  fi

  log_info "starting $name (./mvnw spring-boot:run in $dir/, log: .run/logs/$name.log)"
  mkdir -p "$LOG_DIR" "$PID_DIR"
  # setsid detaches the wrapper into its own process group for clean shutdown.
  (cd "$ROOT_DIR/$dir" && exec setsid ./mvnw -DskipTests spring-boot:run) \
    >"$LOG_DIR/$name.log" 2>&1 &
  echo $! > "$PID_DIR/$name.pid"
  return 0
}

service_process_alive() {
  local pid; pid="$(svc_pid "$1")"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

wait_service() { # wait_service <name> <timeout-seconds>
  local name="$1" timeout="$2" elapsed=0
  while (( elapsed < timeout )); do
    if http_healthy "$name"; then
      log_ok "$name is healthy on :$(svc_port "$name") (after ${elapsed}s)"
      return 0
    fi
    if ! service_process_alive "$name"; then
      log_err "$name process exited before becoming healthy"
      dump_log_tail "$name"
      return 1
    fi
    sleep "$POLL_INTERVAL"; elapsed=$((elapsed + POLL_INTERVAL))
    (( elapsed % 30 == 0 )) && log_info "still waiting for $name... (${elapsed}s/${timeout}s)"
  done
  log_err "$name did not become healthy within ${timeout}s"
  dump_log_tail "$name"
  return 1
}

wait_wave2() { # all parallel services; fail fast if any process dies
  local -a pending=("${WAVE2_ORDER[@]}") remaining=() name elapsed=0
  local deadline=$SERVICE_TIMEOUT
  while (( ${#pending[@]} > 0 )) && (( elapsed < deadline )); do
    remaining=()
    for name in "${pending[@]}"; do
      if http_healthy "$name"; then
        log_ok "$name is healthy on :$(svc_port "$name")"
      elif ! service_process_alive "$name"; then
        log_err "$name process exited before becoming healthy"
        dump_log_tail "$name"
        return 1
      else
        remaining+=("$name")
      fi
    done
    pending=("${remaining[@]}")
    ((${#pending[@]} > 0)) && sleep "$POLL_INTERVAL"
    elapsed=$((elapsed + POLL_INTERVAL))
  done
  if (( ${#pending[@]} > 0 )); then
    log_err "services not healthy after ${deadline}s: ${pending[*]}"
    for name in "${pending[@]}"; do dump_log_tail "$name"; done
    return 1
  fi
  return 0
}

# ----------------------------------------------------------------------------
# Phases
# ----------------------------------------------------------------------------
preflight() {
  log_step "preflight checks"
  local missing=() tool
  for tool in curl docker grep awk sed; do
    command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
  done
  if (( ${#missing[@]} > 0 )); then
    log_err "missing required tools: ${missing[*]}"; exit 1
  fi
  if (( BASH_VERSINFO[0] < 4 )); then
    log_err "bash >= 4 required (found ${BASH_VERSINFO[0]})"; exit 1
  fi
  command -v java >/dev/null 2>&1 || { log_err "java not found on PATH"; exit 1; }
  docker compose version >/dev/null 2>&1 || { log_err "'docker compose' plugin not available"; exit 1; }
  ensure_java
  if ! docker info >/dev/null 2>&1; then
    log_err "docker daemon is not reachable — start Docker first"; exit 1
  fi
  local name dir
  for name in config-server discovery "${WAVE2_ORDER[@]}"; do
    dir="$(svc_dir "$name")"
    [[ -x "$ROOT_DIR/$dir/mvnw" ]] || { log_err "$dir/mvnw is missing or not executable"; exit 1; }
  done
  log_ok "environment ready (docker compose, java, curl)"
}

start_infra() {
  log_step "phase 1/4: infrastructure (docker compose)"
  docker compose -f "$COMPOSE_FILE" up -d
  local -A checkers=(
    [ms_pg_sql]=pg_ready [mongo_db]=mongo_ready
    [ms_kafka]=kafka_ready [ms-mail-dev]=mail_ready
  )
  local container elapsed ok
  for container in "${INFRA_CONTAINERS[@]}"; do
    elapsed=0; ok=0
    while (( elapsed < INFRA_TIMEOUT )); do
      if container_running "$container" && "${checkers[$container]}"; then ok=1; break; fi
      sleep "$POLL_INTERVAL"; elapsed=$((elapsed + POLL_INTERVAL))
    done
    if (( ok )); then
      log_ok "infra: $container ready (after ${elapsed}s)"
    else
      log_err "infra: $container not ready after ${INFRA_TIMEOUT}s"
      docker logs --tail "$LOG_TAIL_LINES" "$container" 2>&1 | sed 's/^/    /' >&2 || true
      exit 1
    fi
  done
  log_info "ensuring required postgres databases exist: ${PG_DATABASES[*]}"
  ensure_pg_database
}

start_services() {
  log_step "phase 2/4: config-server (all services import their config from it)"
  start_service config-server || exit 1
  wait_service config-server "$CONFIG_TIMEOUT" || exit 1

  log_step "phase 3/4: discovery/Eureka (registers every downstream service)"
  start_service discovery || exit 1
  wait_service discovery "$DISCOVERY_TIMEOUT" || exit 1

  log_step "phase 4/4: gateway + business services (independent at startup — parallel)"
  local name
  for name in "${WAVE2_ORDER[@]}"; do
    start_service "$name" || exit 1   # order/product/payment/customer/gateway fail fast on port clashes
  done
  wait_wave2 || exit 1
}

print_summary() {
  log ""
  log "${C_GREEN}==== System is UP ====${C_RESET}"
  local name pid
  printf '  %-18s %-6s %-11s %s\n' "SERVICE" "PORT" "STATUS" "LOG"
  for name in config-server discovery "${WAVE2_ORDER[@]}"; do
    pid="$(svc_pid "$name")"
    printf '  %-18s %-6s %-11s %s\n' "$name" "$(svc_port "$name")" \
      "$(http_healthy "$name" && echo "healthy" || echo "DOWN")" \
      ".run/logs/$name.log"
  done
  log ""
  log "  Eureka console : http://localhost:8761"
  log "  Gateway        : http://localhost:8222  (e.g. /api/v1/products)"
  log "  Mail inbox     : http://localhost:1080  |  pgAdmin :5050  |  mongo-express :8081"
  log ""
}

# ----------------------------------------------------------------------------
# Subcommands
# ----------------------------------------------------------------------------
cmd_start() {
  local skip_infra=0 infra_only=0
  while (( $# > 0 )); do
    case "$1" in
      --skip-infra) skip_infra=1 ;;
      --infra-only) infra_only=1 ;;
      *) log_err "unknown option: $1"; usage; exit 1 ;;
    esac
    shift
  done
  mkdir -p "$LOG_DIR" "$PID_DIR"
  # Guard against a second concurrent 'start': its EXIT cleanup would otherwise
  # kill the services of the first instance (they share the PID files).
  if command -v flock >/dev/null 2>&1; then
    exec 9>"$PID_DIR/.system.lock"
    flock -n 9 || { log_err "another 'start' instance is already running (lock: $PID_DIR/.system.lock)"; exit 1; }
  fi

  trap on_interrupt INT TERM HUP
  trap cleanup EXIT

  preflight
  if (( ! skip_infra )); then
    start_infra
  else
    log_warn "--skip-infra: assuming docker-compose infrastructure is already running"
  fi
  if (( infra_only )); then
    log_ok "infrastructure started; skipping application services (--infra-only)"
    return 0
  fi
  start_services
  print_summary
  log "System is running in the foreground. Press Ctrl+C to stop everything."
  log ""
  # Block until interrupted; if a service dies unexpectedly, shut down cleanly.
  while true; do
    local name
    for name in config-server discovery "${WAVE2_ORDER[@]}"; do
      if ! service_process_alive "$name" && ! http_healthy "$name"; then
        log_err "$name is no longer running — shutting the system down"
        dump_log_tail "$name"
        exit 1
      fi
    done
    sleep 5
  done
}

cmd_stop() {
  local with_infra=0
  [[ "${1:-}" == "--infra" ]] && with_infra=1
  log_step "stopping microservices"
  if ! ls "$PID_DIR"/*.pid >/dev/null 2>&1; then
    log_warn "no PID files found — services were not started by this script"
    if (( with_infra )); then
      log_step "stopping infrastructure (docker compose stop)"
      docker compose -f "$COMPOSE_FILE" stop
    fi
    return 0
  fi
  cleanup
  if (( with_infra )); then
    log_step "stopping infrastructure (docker compose stop)"
    docker compose -f "$COMPOSE_FILE" stop
    log_ok "infrastructure stopped (volumes preserved; use 'docker compose down' to drop them)"
  fi
}

cmd_status() {
  local name pid status
  printf '  %-18s %-6s %-9s %s\n' "SERVICE" "PORT" "PID" "HEALTH"
  for name in config-server discovery "${WAVE2_ORDER[@]}"; do
    pid="$(svc_pid "$name")"; status="${C_RED}DOWN${C_RESET}"
    http_healthy "$name" && status="${C_GREEN}healthy${C_RESET}"
    printf '  %-18s %-6s %-9s %b\n' "$name" "$(svc_port "$name")" "${pid:--}" "$status"
  done
}

cmd_logs() {
  local name="${1:-}"
  [[ -z "$name" ]] && { log_err "usage: $0 logs <service>"; exit 1; }
  svc_field "$name" 1 >/dev/null || { log_err "unknown service '$name'"; exit 1; }
  tail -n 100 -f "$LOG_DIR/$name.log"
}

usage() {
  cat <<'EOF'
Usage: ./start-system.sh [command] [options]

Commands:
  start [--skip-infra | --infra-only]  Start the whole system in dependency order
                                       (infrastructure -> config-server -> discovery
                                       -> gateway + business services in parallel).
                                       Runs in the foreground; Ctrl+C stops everything.
  stop  [--infra]                      Stop the services started by this script
                                       (add --infra to also stop docker containers).
  status                               Print a health table for all services.
  logs <service>                       Follow the log of one service.
  help                                 Show this help.

Options:
  --skip-infra   Do not touch docker compose (assume infrastructure is running).
  --infra-only   Start only the docker compose infrastructure and exit.
  --infra        (stop only) Also stop the docker compose containers.

Environment overrides: JAVA_HOME, LOG_DIR, PID_DIR, INFRA_TIMEOUT, CONFIG_TIMEOUT,
DISCOVERY_TIMEOUT, SERVICE_TIMEOUT, POLL_INTERVAL, LOG_TAIL_LINES, PG_USER.
If JAVA_HOME is unset, the script auto-detects a JDK 17/21 from ~/.jdks,
~/.sdkman or /usr/lib/jvm (Lombok 1.18.30 cannot compile on JDK 22+).
EOF
}

# ----------------------------------------------------------------------------
# Entry point
# ----------------------------------------------------------------------------
case "${1:-start}" in
  start)  shift || true; cmd_start "$@" ;;
  stop)   shift || true; cmd_stop "$@" ;;
  status) cmd_status ;;
  logs)   shift || true; cmd_logs "$@" ;;
  help|-h|--help) usage ;;
  *) log_err "unknown command: $1"; usage; exit 1 ;;
esac
