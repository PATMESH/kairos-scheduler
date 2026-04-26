#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

JAR="target/scheduler-service-0.0.1-SNAPSHOT.jar"
PEERS="scheduler-1:127.0.0.1:9090,scheduler-2:127.0.0.1:9091,scheduler-3:127.0.0.1:9092"
STORAGE_DIR="/tmp/kairos-raft-3node-test"
LOG_DIR="$ROOT_DIR/target/local-raft-logs"

if [[ "${1:-}" == "--clean" ]]; then
  pkill -f "$JAR" 2>/dev/null || true
  rm -rf "$STORAGE_DIR" "$LOG_DIR"
fi

mkdir -p "$STORAGE_DIR" "$LOG_DIR"

if [[ ! -f "$JAR" ]]; then
  ./mvnw -q -DskipTests package
fi

start_node() {
  local node_id="$1"
  local raft_port="$2"
  local server_port="$3"
  local log_file="$LOG_DIR/$node_id.log"

  NODE_ID="$node_id" \
  RAFT_PORT="$raft_port" \
  RAFT_ADDRESS="127.0.0.1" \
  SERVER_PORT="$server_port" \
  RAFT_PEERS="$PEERS" \
  nohup java -jar "$JAR" \
    --kairos.raft.storage-dir="$STORAGE_DIR" \
    >"$log_file" 2>&1 &

  echo "$node_id pid=$! http=$server_port raft=$raft_port log=$log_file"
}

start_node scheduler-1 9090 8081
start_node scheduler-2 9091 8082
start_node scheduler-3 9092 8083

echo
echo "Wait 10-15 seconds, then run:"
echo "curl localhost:8081/raft/status"
echo "curl localhost:8082/raft/status"
echo "curl localhost:8083/raft/status"
echo
echo "Logs:"
echo "tail -f $LOG_DIR/scheduler-1.log $LOG_DIR/scheduler-2.log $LOG_DIR/scheduler-3.log"
