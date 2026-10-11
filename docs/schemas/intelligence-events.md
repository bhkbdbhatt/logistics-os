# Intelligence & Optimization Event Schemas

> Status: Accepted · Related: [common-types.md](common-types.md) · [orchestration-events.md](orchestration-events.md) · [../intelligence/README.md](../intelligence/README.md) · [../events.md](../events.md)

Avro contracts for the intelligence layer (L4). L4 **advises, never writes domain state** ([integrations.md §5](../integrations.md)). Outputs are predictions and recommendations that orchestration consumes as `DecisionContext.signals` and `DecisionContext.options` ([../orchestration/decision-context.md](../orchestration/decision-context.md)). Namespace `io.logisticsos.intelligence.v1`.

Topics:

| Topic | Key | Purpose |
|---|---|---|
| `intel.prediction.v1` | `shipmentId` | ETA / delay / risk predictions |
| `intel.recommendation.v1` | `subjectId` | ranked action options (route, carrier, load, slot) |
| `intel.model.events.v1` | `modelId` | model lifecycle, drift, registry |
| `intel.simulation.v1` | `scenarioId` | network what-if requests/results |
| `intel.feedback.v1` | `entityId` | realized-outcome labels for monitoring/retraining |

## 1. Shared enums

```json
{
  "type": "enum",
  "name": "Objective",
  "namespace": "io.logisticsos.intelligence.v1",
  "symbols": ["MIN_COST", "MIN_TIME", "MAX_SERVICE", "BALANCE_COST_SERVICE", "MIN_EMISSIONS"]
}
```

```json
{
  "type": "enum",
  "name": "RecommendationType",
  "namespace": "io.logisticsos.intelligence.v1",
  "symbols": ["ROUTE_PLAN", "CARRIER_ASSIGNMENT", "LOAD_BUILD", "WAVE_PLAN",
              "SLOTTING", "INVENTORY_PLACEMENT", "PRIORITIZATION", "REOPT_SNAPSHOT"]
}
```

```json
{
  "type": "enum",
  "name": "ModelKind",
  "namespace": "io.logisticsos.intelligence.v1",
  "symbols": ["ETA", "DELAY_RISK", "TENDER_ACCEPTANCE", "CARRIER_PERFORMANCE",
              "ROUTE_OPTIMIZER", "LOAD_OPTIMIZER", "DEMAND_FORECAST", "ANOMALY"]
}
```

## 2. Predictions — `intel.prediction.v1`

```json
{
  "type": "record",
  "name": "EtaPrediction",
  "namespace": "io.logisticsos.intelligence.v1",
  "doc": "Quantile ETA prediction for a shipment/trip. Consumed as a DecisionContext signal.",
  "fields": [
    { "name": "predictionId", "type": "string" },
    { "name": "shipmentId", "type": "string" },
    { "name": "legIndex", "type": ["null", "int"], "default": null },
    { "name": "baselineEtaAt", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "p10EtaAt", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "p50EtaAt", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "p90EtaAt", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "predictedDelayMin", "type": "int", "doc": "p50 minus baseline, signed" },
    { "name": "confidence", "type": "double", "doc": "0..1 model confidence" },
    { "name": "modelId", "type": "string" },
    { "name": "modelVersion", "type": "int" },
    { "name": "featuresHash", "type": "string", "doc": "hash of the feature vector for reproducibility" },
    { "name": "featuresAsOf", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "validUntil", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "computedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

## 3. Recommendations — `intel.recommendation.v1`

```json
{
  "type": "record",
  "name": "RecommendationOption",
  "namespace": "io.logisticsos.intelligence.v1",
  "doc": "One candidate action with expected outcomes; maps into DecisionContext.options.",
  "fields": [
    { "name": "optionId", "type": "string" },
    { "name": "action", "type": "string", "doc": "command intent, e.g. REROUTE, REBOOK_CARRIER, BUILD_LOAD" },
    { "name": "description", "type": "string" },
    { "name": "expectedOutcome", "type": "string" },
    { "name": "expectedCost", "type": ["null", "io.logisticsos.types.Money"], "default": null },
    { "name": "expectedTimeMin", "type": ["null", "int"], "default": null },
    { "name": "riskScore", "type": ["null", "double"], "default": null },
    { "name": "confidence", "type": ["null", "double"], "default": null },
    { "name": "score", "type": ["null", "double"], "default": null },
    { "name": "constraintsSatisfied", "type": "boolean" },
    { "name": ["null", {"type": "array", "items": {
        "type": "record", "name": "OptionParam", "fields": [
          { "name": "key", "type": "string" },
          { "name": "value", "type": "string" }
        ] } }], "name": "params", "default": null }
  ]
}
```

```json
{
  "type": "record",
  "name": "Recommendation",
  "namespace": "io.logisticsos.intelligence.v1",
  "doc": "Ranked options for a subject. Orchestration decides whether/how to apply.",
  "fields": [
    { "name": "recommendationId", "type": "string" },
    { "name": "type", "type": "RecommendationType" },
    { "name": "subject", "type": "io.logisticsos.orchestration.v1.SubjectRefs" },
    { "name": "objective", "type": "Objective" },
    { "name": "options", "type": { "type": "array", "items": "RecommendationOption" } },
    { "name": "recommendedOptionId", "type": ["null", "string"], "default": null },
    { "name": "modelId", "type": "string" },
    { "name": "modelVersion", "type": "int" },
    { "name": "solverId", "type": ["null", "string"], "default": null },
    { "name": "confidence", "type": "double" },
    { "name": "featuresHash", "type": "string" },
    { "name": "validUntil", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "generatedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": ["null", "string"], "name": "notes", "default": null }
  ]
}
```

## 4. Model lifecycle / drift — `intel.model.events.v1`

```json
{
  "type": "record",
  "name": "ModelDeployed",
  "namespace": "io.logisticsos.intelligence.v1",
  "fields": [
    { "name": "modelId", "type": "string" },
    { "name": "kind", "type": "ModelKind" },
    { "name": "version", "type": "int" },
    { "name": "metrics", "type": { "type": "array", "items": {
        "type": "record", "name": "Metric", "fields": [
          { "name": "name", "type": "string" },
          { "name": "value", "type": "double" }
        ] } } },
    { "name": "trainingDataWindowStart", "type": ["null", { "type": "long", "logicalType": "timestamp-millis" }], "default": null },
    { "name": "trainingDataWindowEnd", "type": ["null", { "type": "long", "logicalType": "timestamp-millis" }], "default": null },
    { "name": "deployedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

```json
{
  "type": "record",
  "name": "ModelDriftDetected",
  "namespace": "io.logisticsos.intelligence.v1",
  "fields": [
    { "name": "modelId", "type": "string" },
    { "name": "kind", "type": "ModelKind" },
    { "name": "version", "type": "int" },
    { "name": "driftType", "type": { "type": "enum", "name": "DriftType",
        "symbols": ["DATA_DRIFT", "CONCEPT_DRIFT", "CALIBRATION", "FEATURE_SKEW"] } },
    { "name": "metric", "type": "string" },
    { "name": "observed", "type": "double" },
    { "name": "threshold", "type": "double" },
    { "name": "severity", "type": "io.logisticsos.orchestration.v1.Severity" },
    { "name": "detectedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

## 5. Network simulation — `intel.simulation.v1`

```json
{
  "type": "record",
  "name": "SimulationRequested",
  "namespace": "io.logisticsos.intelligence.v1",
  "fields": [
    { "name": "scenarioId", "type": "string" },
    { "name": "name", "type": "string" },
    { "name": "baselineRef", "type": "string", "doc": "snapshot id of current network state" },
    { "name": "horizonStart", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "horizonEnd", "type": { "type": "long", "logicalType": "timestamp-millis" } },
    { "name": "levers", "type": { "type": "array", "items": {
        "type": "record", "name": "ScenarioLever", "fields": [
          { "name": "lever", "type": { "type": "enum", "name": "LeverType",
            "symbols": ["CAPACITY","DEMAND_SHIFT","LANE_CLOSURE","CARRIER_MIX","COST","SLA_TARGET",
                        "NODE_OPEN","NODE_CLOSE","MODAL_SHIFT","LABOR"] } },
          { "name": "target", "type": "string" },
          { "name": "delta", "type": "string" }
        ] } } },
    { "name": "monteCarloRuns", "type": ["null", "int"], "default": null },
    { "name": "objective", "type": "Objective" },
    { "name": "requestedBy", "type": "string" },
    { "name": "requestedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

```json
{
  "type": "record",
  "name": "SimulationCompleted",
  "namespace": "io.logisticsos.intelligence.v1",
  "fields": [
    { "name": "scenarioId", "type": "string" },
    { "name": "status", "type": { "type": "enum", "name": "SimulationStatus",
        "symbols": ["COMPLETED", "FAILED", "TIMEOUT"] } },
    { "name": "costDeltaPct", "type": ["null", "double"], "default": null },
    { "name": "otdDeltaPct", "type": ["null", "double"], "default": null },
    { "name": "utilizationPct", "type": ["null", "double"], "default": null },
    { "name": "emissionsDeltaPct", "type": ["null", "double"], "default": null },
    { "name": "p10SlaPct", "type": ["null", "double"], "default": null },
    { "name": "p90SlaPct", "type": ["null", "double"], "default": null },
    { "name": "solverId", "type": "string" },
    { "name": "solveTimeMs", "type": "long" },
    { "name": "artifactRef", "type": ["null", "string"], "default": null },
    { "name": "completedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

## 6. Feedback labels — `intel.feedback.v1`

```json
{
  "type": "record",
  "name": "FeedbackLabel",
  "namespace": "io.logisticsos.intelligence.v1",
  "doc": "Realized vs predicted outcome, emitted when ground truth arrives (POD, delivery, tender result).",
  "fields": [
    { "name": "entityType", "type": "string", "doc": "shipment|tender|route" },
    { "name": "entityId", "type": "string" },
    { "name": "kind", "type": "ModelKind" },
    { "name": "predictionId", "type": ["null", "string"], "default": null },
    { "name": "predictedValue", "type": "double" },
    { "name": "actualValue", "type": "double" },
    { "name": "errorAbs", "type": "double" },
    { "name": "modelId", "type": "string" },
    { "name": "modelVersion", "type": "int" },
    { "name": "observedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

## 7. Contract with orchestration

| L4 output | Consumed by orchestration as | Then |
|---|---|---|
| `EtaPrediction` (`intel.prediction.v1`) | `DecisionContext.signals[]` (with `modelId`, `confidence`, `featuresHash`) | triggers `TIME_SLA_*`, `LATE_INBOUND`; gates on confidence |
| `Recommendation` (`intel.recommendation.v1`) | `DecisionContext.options[]` (mapped by `expectedCost`, `expectedTimeMin`, `riskScore`, `confidence`) | policy scores/ranks; routes auto/human/partner |
| `ReoptimizationCompleted` | new candidate `Recommendation`s | re-plan decision |
| `SimulationCompleted` (`intel.simulation.v1`) | policy tuning input (offline, not per-decision) | adjust thresholds/network policy |

Orchestration **never** applies a recommendation without a policy decision ([../../../docs/orchestration/decision-context.md](../orchestration/decision-context.md)). Predictions and recommendations are **advisory**; the `DecisionRecord` captures the final choice and whether it overrode the recommendation.

## 8. Idempotency

- `EtaPrediction`/`Recommendation` dedupe on `predictionId`/`recommendationId`; consumers version-guard by `generatedAt`/`validUntil`, ignoring stale (expired) outputs.
- `FeedbackLabel` dedupe on `(entityType, entityId, kind, observedAt)`; never applied twice in training pipelines.
- L4 outputs are recomputable from inputs + model version (`modelId`, `modelVersion`, `featuresHash`), so replay is safe.
