# Analytics & Control-Tower Event Schemas

> Status: Accepted · Related: [common-types.md](common-types.md) · [orchestration-events.md](orchestration-events.md) · [intelligence-events.md](intelligence-events.md) · [../analytics/README.md](../analytics/README.md) · [../events.md](../events.md)

Avro contracts for L6/L7 operational outputs. These are **derived/advisory** records: they project or aggregate canonical events; they are **not** a system of record for money or inventory ([data-model.md](../data-model.md)). Namespace `io.logisticsos.analytics.v1`.

| Topic | Key | Purpose |
|---|---|---|
| `analytics.alert.v1` | `subjectId` | operational alert raised by the control-tower rule engine |
| `analytics.cost.attribution.v1` | `shipmentId` | per-shipment cost attribution (estimated → trued-up) |
| `analytics.scorecard.v1` | `carrierId` | carrier scorecard (scoped per lane/service) |

## 1. Shared enums

```json
{
  "type": "enum",
  "name": "AlertType",
  "namespace": "io.logisticsos.analytics.v1",
  "symbols": ["SLA_AT_RISK", "SLA_BREACHED", "EXCEPTION_OPENED", "ESCALATION_BREACH",
              "TENDER_DECLINED", "UNASSIGNED_LOAD", "DWELL_EXCEEDED", "DOCK_OVERBOOKED",
              "AUTOMATION_BLOCKED", "INVENTORY_SHORT", "COST_OVERRUN", "VIP_AT_RISK",
              "INTEGRATION_LAG", "DLQ_BACKLOG", "MODEL_DRIFT", "POD_MISSING", "ATP_EXCEPTION"]
}
```

```json
{
  "type": "enum",
  "name": "CostCategory",
  "namespace": "io.logisticsos.analytics.v1",
  "symbols": ["VARIABLE_DIRECT", "VARIABLE_ALLOCATED", "FIXED_ALLOCATED"]
}
```

```json
{
  "type": "enum",
  "name": "CostStatus",
  "namespace": "io.logisticsos.analytics.v1",
  "symbols": ["TENTATIVE", "REFINED", "TRUED_UP", "FINAL"]
}
```

```json
{
  "type": "enum",
  "name": "ScoreDimension",
  "namespace": "io.logisticsos.analytics.v1",
  "symbols": ["COST", "OTD", "OTP", "TRANSIT_VARIANCE", "DAMAGE_CLAIMS",
              "TENDER_ACCEPTANCE", "CAPACITY_COVERAGE", "COMPLIANCE_SAFETY",
              "SUSTAINABILITY", "INTEGRATION_QUALITY", "DISPUTE_RATE"]
}
```

```json
{
  "type": "enum",
  "name": "ScoreGrade",
  "namespace": "io.logisticsos.analytics.v1",
  "symbols": ["A", "B", "C", "D"]
}
```

## 2. Operational alerts — `analytics.alert.v1`

```json
{
  "type": "record",
  "name": "OperationalAlert",
  "namespace": "io.logisticsos.analytics.v1",
  "doc": "Raised by the control-tower rule engine over the hot/near-real-time tier. Distinct from orch.trigger/exception (planning) — this is the UI-facing operational alert.",
  "fields": [
    { "name": "alertId", "type": "string" },
    { "name": "type", "type": "AlertType" },
    { "name": "severity", "type": "io.logisticsos.orchestration.v1.Severity" },
    { "name": "subject", "type": "io.logisticsos.orchestration.v1.SubjectRefs" },
    { "name": "title", "type": "string" },
    { "name": "description", "type": "string" },
    { "name": "metrics", "type": { "type": "array", "items": {
        "type": "record", "name": "MetricValue", "fields": [
          { "name": "name", "type": "string" },
          { "name": "value", "type": "double" },
          { "name": "unit", "type": ["null", "string"], "default": null }
        ] } } },
    { "name": "triggerRef", "type": ["null", "string"], "default": null },
    { "name": "exceptionRef", "type": ["null", "string"], "default": null },
    { "name": "suggestedActions", "type": ["null", { "type": "array", "items": "string" }], "default": null,
      "doc": "action ids a one-click UI may invoke; all map to orch.command.v1" },
    { "name": "detectedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "slaDeadline", "type": ["null", { "type": "long", "logicalType": "timestamp-millis" }], "default": null },
    { "name": "acknowledgedBy", "type": ["null", "string"], "default": null }
  ]
}
```

## 3. Shipment cost attribution — `analytics.cost.attribution.v1`

```json
{
  "type": "record",
  "name": "CostComponent",
  "namespace": "io.logisticsos.analytics.v1",
  "fields": [
    { "name": "category", "type": "CostCategory" },
    { "name": "costType", "type": "string", "doc": "linehaul|fuel|accessorial|labor|packaging|fixed_overhead|..." },
    { "name": "driver", "type": "string", "doc": "allocation driver: mile|weight|volume|cube|pallet|stop|line|order|unit" },
    { "name": "runRate", "type": "double", "doc": "cost per driver unit used for allocation" },
    { "name": "amount", "type": "io.logisticsos.types.Money" },
    { "name": "source", "type": { "type": "enum", "name": "CostSource",
        "symbols": ["RATE_CARD", "CARRIER_INVOICE", "TIMESHEET", "STANDARD_COST", "ALLOCATED_POOL"] } },
    { "name": "confidence", "type": "double" }
  ]
}
```

```json
{
  "type": "record",
  "name": "ShipmentCostAttribution",
  "namespace": "io.logisticsos.analytics.v1",
  "doc": "Per-shipment cost roll-up. Estimated in real-time from standard rates, trued-up as actuals arrive. Derived, not a ledger (Billing remains SoR for charges).",
  "fields": [
    { "name": "attributionId", "type": "string" },
    { "name": "shipmentId", "type": "string" },
    { "name": "orderId", "type": ["null", "string"], "default": null },
    { "name": "loadId", "type": ["null", "string"], "default": null },
    { "name": "components", "type": { "type": "array", "items": "CostComponent" } },
    { "name": "variableTotal", "type": "io.logisticsos.types.Money" },
    { "name": "fixedTotal", "type": "io.logisticsos.types.Money" },
    { "name": "total", "type": "io.logisticsos.types.Money" },
    { "name": "revenue", "type": ["null", "io.logisticsos.types.Money"], "default": null },
    { "name": "margin", "type": ["null", "io.logisticsos.types.Money"], "default": null },
    { "name": "marginPct", "type": ["null", "double"], "default": null },
    { "name": "costStatus", "type": "CostStatus" },
    { "name": "modelVersion", "type": ["null", "int"], "default": null },
    { "name": "computedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

## 4. Carrier scorecards — `analytics.scorecard.v1`

```json
{
  "type": "record",
  "name": "ScorecardDimension",
  "namespace": "io.logisticsos.analytics.v1",
  "fields": [
    { "name": "dimension", "type": "ScoreDimension" },
    { "name": "rawValue", "type": "double" },
    { "name": "normalizedScore", "type": "double", "doc": "0..100 within the comparison cohort" },
    { "name": "weight", "type": "double" },
    { "name": "confidence", "type": "double" },
    { "name": "sampleSize", "type": "int" }
  ]
}
```

```json
{
  "type": "record",
  "name": "CarrierScorecard",
  "namespace": "io.logisticsos.analytics.v1",
  "fields": [
    { "name": "scorecardId", "type": "string" },
    { "name": "carrierId", "type": "string" },
    { "name": "scope", "type": { "type": "enum", "name": "ScorecardScope",
        "symbols": ["GLOBAL", "LANE", "MODE", "SERVICE"] } },
    { "name": "laneKey", "type": ["null", "string"], "default": null },
    { "name": "mode", "type": ["null", "string"], "default": null },
    { "name": "periodStart", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "periodEnd", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "dimensions", "type": { "type": "array", "items": "ScorecardDimension" } },
    { "name": "compositeScore", "type": "double" },
    { "name": "grade", "type": "ScoreGrade" },
    { "name": "rank", "type": ["null", "int"], "default": null },
    { "name": "volume", "type": "int" },
    { "name": "weightsProfile", "type": "string", "doc": "named policy profile used for weighting" },
    { "name": "computedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

## 5. Idempotency & semantics

- **Alerts** dedupe on `(type, subject.id, slaDeadline)` within a firing window; re-fire only after `resolved` or window advance. State (ack/resolved) is a compacted projection, not an event append storm.
- **Cost attribution** is keyed `(shipmentId, costStatus)`; a later status **supersedes** earlier ones (TENTATIVE → REFINED → TRUED_UP → FINAL). Consumers keep the highest-status/most-recent record.
- **Scorecards** dedupe on `(carrierId, scope, laneKey, mode, periodEnd)`; a recomputation for the same period overwrites.
- All three are **derived and replayable** from canonical events; losing them loses no domain truth.
