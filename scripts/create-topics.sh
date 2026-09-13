#!/usr/bin/env bash
set -euo pipefail

BROKER="${KAFKA_BROKER:-localhost:9092}"
TOPIC="fieldops.work-orders.events"
DLT="fieldops.work-orders.events-dlt"

create_topic() {
  local name="$1" partitions="$2" retention="$3"
  if docker compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list | grep -qx "$name"; then
    echo "Topic $name ya existe"
    return
  fi
  docker compose exec -T kafka kafka-topics \
    --bootstrap-server localhost:9092 \
    --create --topic "$name" \
    --partitions "$partitions" \
    --replication-factor 1 \
    --config retention.ms="$retention"
  echo "Topic $name creado"
}

create_topic "$TOPIC" 3 2592000000
create_topic "$DLT" 1 2592000000

docker compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic "$TOPIC"