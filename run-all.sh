#!/usr/bin/env bash
# Starts the whole system in dependency order:
#   core-banking-api :8090  ->  accounts/transaction/service MCP servers :8091-8093  ->  bot :8080
# Ctrl-C stops everything. Logs go to ./logs/<module>.log
set -euo pipefail
cd "$(dirname "$0")"

MVNW=./mvnw
LOG_DIR=logs
mkdir -p "$LOG_DIR"
PIDS=()

cleanup() {
  echo
  echo "stopping..."
  for pid in "${PIDS[@]:-}"; do kill "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

wait_for() {  # wait_for <port> <name> — succeeds as soon as the port answers HTTP (any status)
  for _ in $(seq 1 60); do
    if curl -s -o /dev/null "http://localhost:$1/"; then echo "  $2 is up"; return 0; fi
    sleep 1
  done
  echo "  $2 did NOT come up — see $LOG_DIR/$2.log" >&2
  return 1
}

start() {  # start <module> <port> <name>
  echo "starting $3 ..."
  $MVNW -o -q -pl "$1" spring-boot:run > "$LOG_DIR/$3.log" 2>&1 &
  PIDS+=($!)
  wait_for "$2" "$3"
}

start core-banking-api      8090 core-banking-api
start accounts-mcp-server    8091 accounts-mcp-server
start transaction-mcp-server 8092 transaction-mcp-server
start service-mcp-server     8093 service-mcp-server

echo "starting bot (foreground) ... open http://localhost:8080"
$MVNW -o -q -pl bot spring-boot:run 2>&1 | tee "$LOG_DIR/bot.log"
