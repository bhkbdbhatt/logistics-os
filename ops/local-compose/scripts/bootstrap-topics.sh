#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:9092}"
TOPICS_FILE="/etc/topics/topics.lst"

echo "waiting for Kafka at ${BOOTSTRAP} ..."
deadline=$((SECONDS + 120))
until kafka-broker-api-versions --bootstrap-server "$BOOTSTRAP" >/dev/null 2>&1; do
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "kafka not reachable after 120s" >&2
    exit 1
  fi
  sleep 2
done
echo "Kafka is ready; creating topics"

while IFS= read -r line; do
  case "$line" in
    ""|\#*) continue ;;
  esac
  IFS=':' read -r name partitions cleanup <<< "$line"
  conf=()
  if [ "${cleanup:-}" = "compact" ]; then
    conf+=(--config cleanup.policy=compact)
    conf+=(--config retention.ms=-1)
  fi
  echo ">> $name (partitions=${partitions:-1}, cleanup=${cleanup:-delete})"
  kafka-topics --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$name" \
    --partitions "${partitions:-1}" \
    --replication-factor 1 \
    "${conf[@]}"
done < "$TOPICS_FILE"

echo "topic bootstrap complete"