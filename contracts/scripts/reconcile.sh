#!/usr/bin/env bash
# Reconcile contracts/payload-registry.json <-> contracts/avro + docs/schemas headings.
# Usage: bash contracts/scripts/reconcile.sh
# CI runs the stronger gate via gradle :contracts:validation:validateSchemas; this script
# adds a human-readable docs-heading cross-check that the Gradle task complements.
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AVRO="$ROOT/contracts/avro"
REG="$ROOT/contracts/payload-registry.json"

# Payloads shared by >1 eventType: doc heading -> avsc file (payloadSchema)
ALIASES="
ShipmentArrivedAtFacility|ShipmentFacilityEvent
ShipmentDepartedFacility|ShipmentFacilityEvent
ShipmentExceptionRaised|ShipmentException
ShipmentExceptionResolved|ShipmentException
"

fail=0
warn=0

# Top-level records that are intentionally not envelope payloads
EXCLUDED_FILE="$ROOT/contracts/validation/registry-exclusions.txt"
excludes=""
if [ -f "$EXCLUDED_FILE" ]; then
  excludes="$(grep -vE '^[[:space:]]*(#|$)' "$EXCLUDED_FILE" | tr -d '\r')"
fi
is_excluded() { # $1 = fullname
  local n="$1" line=""
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    [ "$line" = "$n" ] && return 0
  done <<< "$excludes"
  return 1
}

find_avsc() { # $1 = full schema name, e.g. io.logisticsos.shipment.v1.ShipmentCreated
  local dotted="$1"
  local ns="${dotted%.*}" name="${dotted##*.}"
  local path="${ns//./\/}"
  [ -f "$AVRO/$path/$name.avsc" ]
}

resolve_alias() { # $1 = doc heading name; echoes avsc base name if aliased else $1
  local n="$1" line="" base=""
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    base="${line}|" ; base="${base%%|*}" ; rest="${line#*|}"
    if [ "$n" = "$base" ]; then echo "$rest"; return; fi
  done <<< "$ALIASES"
  echo "$n"
}

# 1) every registry payloadSchema must resolve to a real .avsc file
while IFS= read -r ref; do
  [ -n "$ref" ] || continue
  if ! find_avsc "$ref"; then
    echo "FAIL: registry payloadSchema has no .avsc: $ref"
    fail=1
  fi
done < <(grep -oE '"payloadSchema"[[:space:]]*:[[:space:]]*"[^"]+"' "$REG" | sed -E 's/.*:.*"([^"]+)"/\1/')

# 2) every top-level RECORD under payload namespaces must be referenced by the registry
while IFS= read -r avsc; do
  ns="${avsc#$AVRO/}" ; ns="${ns%/*}" ; ns="${ns//\//.}"
  case "$ns" in
    io.logisticsos.shipment.v1|io.logisticsos.mdm.v1|io.logisticsos.orchestration.v1|io.logisticsos.obs.v1) ;;
    *) continue ;;
  esac
  type="$(grep -m1 -oE '"type"[[:space:]]*:[[:space:]]*"(record|enum|fixed)"' "$avsc" | sed -E 's/.*"([^"]+)"/\1/')"
  [ "$type" = "record" ] || continue
  name="$(grep -m1 -oE '"name"[[:space:]]*:[[:space:]]*"[A-Za-z][^"]*"' "$avsc" | sed -E 's/.*"([^"]+)"/\1/')"
  full="$ns.$name"
  if ! grep -qE "\"payloadSchema\"[[:space:]]*:[[:space:]]*\"$full\"" "$REG"; then
    if is_excluded "$full"; then
      echo "INFO : excluded from registry by lifetime: $full"
    else
      echo "FAIL: .avsc top-level record missing from payload-registry.json: $full"
      fail=1
    fi
  fi
done < <(find "$AVRO" -name '*.avsc' | sort)

# 3) docs headings in shipment-events.md / reference-data.md must map to a payload avsc (or an alias)
for doc in shipment-events reference-data orchestration-events; do
  [ -f "$ROOT/docs/schemas/$doc.md" ] || continue
  while IFS= read -r tok; do
    [ -n "$tok" ] || continue
    case "$tok" in
      Shipment*|Trigger*|ExceptionCase*|Decision*|Escalation*|Reoptimization*|Orchestration*|Customer|Carrier|Vehicle|Location|Reference*|Telemetry*|Scan*|CarrierStatus*) ;; *) continue ;;
    esac
    base="$(resolve_alias "$tok")"
    if ! find . -iname "$base.avsc" >/dev/null 2>&1; then :; fi
    if ! find "$AVRO" -name "$base.avsc" | grep -q .; then
      echo "WARN : docs/schemas/$doc.md heading maps to no .avsc (backlog or not-yet-contracted): '$tok' (expected $base.avsc)"
      warn=1
    fi
  done < <(grep -oE '`[A-Z][A-Za-z0-9]*(/|`)' "$ROOT/docs/schemas/$doc.md" | tr -d '`' | tr -d '/')
done

if [ "$fail" -ne 0 ]; then
  echo "reconcile.sh FAILED"
  exit 1
fi
echo "reconcile.sh OK"
exit 0