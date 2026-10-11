#!/usr/bin/env bash
# Snapshot the current contracts/avro tree into contracts/baselines/ for drift tracking.
#   --write   copy avro into baselines/latest (default: verify baselines/latest matches, exit non-zero on drift)
# Compatibility checks against the schema registry (BACKWARD) happen in CI against the deployed registry.
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AVRO="$ROOT/contracts/avro"
BL="$ROOT/contracts/baselines/latest"

if [ "${1:-}" = "--write" ]; then
  rm -rf "$BL"
  mkdir -p "$BL"
  cp -R "$AVRO/." "$BL/"
  echo "baseline written to contracts/baselines/latest ($(find "$AVRO" -name '*.avsc' | wc -l) schemas)"
  exit 0
fi

if [ ! -d "$BL" ]; then
  echo "no baseline at contracts/baselines/latest — run: bash contracts/scripts/snapshot-baselines.sh --write"
  exit 1
fi

if diff -r "$AVRO" "$BL" >/dev/null 2>&1; then
  echo "baseline drift: none"
else
  echo "baseline drift detected (contracts changed). If intentional, run:"
  echo "  bash contracts/scripts/snapshot-baselines.sh --write"
  diff -r "$AVRO" "$BL" | head -40
  exit 1
fi