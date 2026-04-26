#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

JAR="target/scheduler-service-0.0.1-SNAPSHOT.jar"
STORAGE_DIR="/tmp/kairos-raft-9node-test"
LOG_DIR="$ROOT_DIR/target/local-raft-logs"

PEERS=""
BASE_RAFT_PORT=9090
BASE_HTTP_PORT=8081

for i in {1..9}; do
  port=$((BASE_RAFT_PORT + i - 1))
  PEERS+="scheduler-$i:127.0.0.1:$port,"
done

PEERS="${PEERS%,}"

echo "Stopping existing nodes..."
pkill -f "$JAR" 2>/dev/null || true
sleep 2

if [[ "${1:-}" == "--clean" ]]; then
  echo "Cleaning storage..."
  rm -rf "$STORAGE_DIR" "$LOG_DIR"
fi

mkdir -p "$STORAGE_DIR" "$LOG_DIR"

if [[ ! -f "$JAR" ]]; then
  ./mvnw -q -DskipTests package
fi

start_node() {
  local i="$1"
  local node_id="scheduler-$i"
  local raft_port=$((BASE_RAFT_PORT + i - 1))
  local server_port=$((BASE_HTTP_PORT + i - 1))
  local log_file="$LOG_DIR/$node_id.log"

  NODE_ID="$node_id" \
  RAFT_PORT="$raft_port" \
  RAFT_ADDRESS="127.0.0.1" \
  SERVER_PORT="$server_port" \
  RAFT_PEERS="$PEERS" \
  nohup java -jar "$JAR" \
    --kairos.raft.storage-dir="$STORAGE_DIR" \
    >"$log_file" 2>&1 &

  echo "$node_id pid=$! http=$server_port raft=$raft_port"
}

for i in {1..9}; do
  start_node "$i"
done

echo
echo "Check:"
for i in {1..9}; do
  port=$((BASE_HTTP_PORT + i - 1))
  echo "curl localhost:$port/raft/status"
done

echo
echo "Logs:"
echo "tail -f $LOG_DIR/*.log"