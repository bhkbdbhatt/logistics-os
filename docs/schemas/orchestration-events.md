# Orchestration Event Schemas

> Status: Accepted · Related: [common-types.md](common-types.md) · [shipment-events.md](shipment-events.md) · [../orchestration/README.md](../orchestration/README.md) · [../orchestration/decision-context.md](../orchestration/decision-context.md) · [../events.md](../events.md)

Avro contracts for the orchestration layer (L5): triggers, exception cases, **Decision Context**, decisions, escalations, re-optimization, and commands. All payloads are the `payload` bytes inside the [EventEnvelope](common-types.md#2-event-envelope). Namespace `io.logisticsos.orchestration.v1`.

Topics:

| Topic | Key | Purpose |
|---|---|---|
| `orch.trigger.v1` | `triggerId` | evaluated trigger conditions |
| `orch.exception.v1` | `caseId` | exception-case lifecycle (extends existing) |
| `orch.decision.v1` | `decisionId` | decision requests / outcomes / deferrals |
| `orch.escalation.v1` | `caseId` | escalation ladder events |
| `orch.reopt.v1` | `reoptId` | re-optimization requests / results |
| `orch.command.v1` | `commandTargetId` | idempotent commands to execution (existing) |

## 1. Shared enums

```json
{
  "type": "enum",
  "name": "Severity",
  "namespace": "io.logisticsos.orchestration.v1",
  "symbols": ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
}
```

```json
{
  "type": "enum",
  "name": "AutonomyTier",
  "namespace": "io.logisticsos.orchestration.v1",
  "symbols": ["L0_FULL_AUTO", "L1_AUTO_NOTIFY", "L2_HUMAN_APPROVE", "L3_HUMAN_ONLY"]
}
```

```json
{
  "type": "enum",
  "name": "TriggerType",
  "namespace": "io.logisticsos.orchestration.v1",
  "symbols": ["TIME_SLA", "QUALITY", "CAPACITY", "FINANCIAL", "CUSTOMER", "EXTERNAL", "SYSTEM", "REOPTIMIZATION"]
}
```

```json
{
  "type": "enum",
  "name": "CaseState",
  "namespace": "io.logisticsos.orchestration.v1",
  "symbols": ["DETECTED", "TRIAGING", "DECIDING", "AUTO_EXECUTING", "AWAITING_HUMAN",
              "AWAITING_PARTNER", "EXECUTING", "VERIFYING", "RECONCILING",
              "ESCALATED", "RESOLVED", "FAILED", "CANCELLED", "CLOSED"]
}
```

```json
{
  "type": "enum",
  "name": "DecisionType",
  "namespace": "io.logisticsos.orchestration.v1",
  "symbols": ["LABOR_REALLOCATION", "CARRIER_REBOOK", "REROUTE", "REOPTIMIZE",
              "CUSTOMER_NOTIFY", "PRIORITIZE", "COMPENSATE", "OTHER"]
}
```

```json
{
  "type": "enum",
  "name": "OptionSource",
  "namespace": "io.logisticsos.orchestration.v1",
  "symbols": ["AUTO_RECOMMENDER", "PLAYBOOK", "HUMAN", "PARTNER"]
}
```

## 2. Referenced subject

```json
{
  "type": "record",
  "name": "SubjectRefs",
  "namespace": "io.logisticsos.orchestration.v1",
  "fields": [
    { "name": ["null", {"type": "array", "items": "string"}], "name": "shipmentIds", "default": null },
    { "name": ["null", {"type": "array", "items": "string"}], "name": "loadIds", "default": null },
    { "name": ["null", {"type": "array", "items": "string"}], "name": "orderIds", "default": null },
    { "name": ["null", {"type": "array", "items": "string"}], "name": "inventoryIds", "default": null },
    { "name": ["null", {"type": "array", "items": "io.logisticsos.types.LocationRef"}], "name": "locations", "default": null },
    { "name": ["null", "string"], "name": "nodeId", "default": null }
  ]
}
```

## 3. Trigger events — `orch.trigger.v1`

```json
{
  "type": "record",
  "name": "TriggerEvaluated",
  "namespace": "io.logisticsos.orchestration.v1",
  "doc": "Emitted when a trigger rule matches on the event stream or a timer fires.",
  "fields": [
    { "name": "triggerId", "type": "string" },
    { "name": "type", "type": "TriggerType" },
    { "name": "severity", "type": "Severity" },
    { "name": "ruleId", "type": "string", "doc": "policy/rule that matched" },
    { "name": "ruleVersion", "type": "int" },
    { "name": "subject", "type": "SubjectRefs" },
    { "name": "condition", "type": "string", "doc": "human-readable predicate that fired" },
    { "name": "matched", "type": "boolean" },
    { "name": "cascadeTemplate", "type": ["null", "string"], "default": null, "doc": "e.g. LATE_INBOUND_CASCADE" },
    { "name": "dedupeKey", "type": "string", "doc": "coalesce key: type:subject:window" },
    { "name": ["null", {"type": "long", "logicalType": "timestamp-millis"}], "name": "windowStart", "default": null },
    { "name": ["null", {"type": "long", "logicalType": "timestamp-millis"}], "name": "windowEnd", "default": null },
    { "name": ["null", "string"], "name": "sourceEventId", "default": null, "doc": "causationId of the triggering event" }
  ]
}
```

## 4. Exception case lifecycle — `orch.exception.v1`

```json
{
  "type": "record",
  "name": "ExceptionCaseOpened",
  "namespace": "io.logisticsos.orchestration.v1",
  "fields": [
    { "name": "caseId", "type": "string" },
    { "name": "triggerId", "type": "string" },
    { "name": "cascadeTemplate", "type": "string" },
    { "name": "severity", "type": "Severity" },
    { "name": "subject", "type": "SubjectRefs" },
    { "name": "state", "type": "CaseState" },
    { "name": "openedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

```json
{
  "type": "record",
  "name": "ExceptionCaseStateChanged",
  "namespace": "io.logisticsos.orchestration.v1",
  "fields": [
    { "name": "caseId", "type": "string" },
    { "name": "fromState", "type": "CaseState" },
    { "name": "toState", "type": "CaseState" },
    { "name": "reason", "type": ["null", "string"], "default": null },
    { "name": ["null", "string"], "name": "decisionId", "default": null },
    { "name": "changedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

```json
{
  "type": "record",
  "name": "ExceptionCaseClosed",
  "namespace": "io.logisticsos.orchestration.v1",
  "fields": [
    { "name": "caseId", "type": "string" },
    { "name": "outcome", "type": { "type": "enum", "name": "CaseOutcome",
        "symbols": ["RESOLVED_AUTO", "RESOLVED_HUMAN", "RESOLVED_PARTNER", "FAILED", "CANCELLED", "DUPLICATE_MERGED"] } },
    { "name": ["null", "string"], "name": "rootCauseCode", "default": null },
    { "name": "durationMs", "type": "long" },
    { "name": "humanTouches", "type": "int" },
    { "name": ["null", "string"], "name": "closedBy", "default": null },
    { "name": "closedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

## 5. Decision Context — the core object

Assembled by the Context Assembler before policy evaluation. This is the single source of truth handed to the Decision Router.

```json
{
  "type": "record",
  "name": "DecisionContext",
  "namespace": "io.logisticsos.orchestration.v1",
  "doc": "Everything needed to make or delegate one operational decision.",
  "fields": [
    { "name": "decisionId", "type": "string" },
    { "name": "caseId", "type": "string" },
    { "name": "decisionType", "type": "DecisionType" },
    { "name": "subject", "type": "SubjectRefs" },
    { "name": "triggerId", "type": "string" },
    { "name": "severity", "type": "Severity" },
    { "name": "facts", "type": { "type": "array", "items": {
        "type": "record", "name": "Fact", "fields": [
          { "name": "key", "type": "string" },
          { "name": "value", "type": "string" },
          { "name": ["null", "string"], "name": "source", "default": null },
          { "name": ["null", "long"], "name": "asOf", "default": null }
        ] } },
      "doc": "snapshot of read-model facts at assembly time" },
    { "name": "signals", "type": { "type": "array", "items": {
        "type": "record", "name": "Signal", "fields": [
          { "name": "name", "type": "string" },
          { "name": "value", "type": "double" },
          { "name": ["null", "string"], "name": "unit", "default": null },
          { "name": ["null", "string"], "name": "modelId", "default": null },
          { "name": ["null", "double"], "name": "confidence", "default": null },
          { "name": ["null", "string"], "name": "featuresHash", "default": null }
        ] } },
      "doc": "prediction/recommendation signals from Intelligence (L4)" },
    { "name": "constraints", "type": { "type": "array", "items": {
        "type": "record", "name": "Constraint", "fields": [
          { "name": "name", "type": "string" },
          { "name": "type", "type": { "type": "enum", "name": "ConstraintType",
            "symbols": ["SLA", "CAPACITY", "COST", "POLICY", "REGULATORY", "TIME", "RESOURCE"] } },
          { "name": "value", "type": "string" },
          { "name": "hard", "type": "boolean" }
        ] } } },
    { "name": "options", "type": { "type": "array", "items": {
        "type": "record", "name": "DecisionOption", "fields": [
          { "name": "optionId", "type": "string" },
          { "name": "description", "type": "string" },
          { "name": "expectedOutcome", "type": "string" },
          { "name": "estimatedCost", "type": ["null", "io.logisticsos.types.Money"], "default": null },
          { "name": "estimatedTimeMin", "type": ["null", "int"], "default": null },
          { "name": "riskScore", "type": ["null", "double"], "default": null },
          { "name": "confidence", "type": ["null", "double"], "default": null },
          { "name": "source", "type": "OptionSource" },
          { "name": "requiresPartnerAck", "type": "boolean" },
          { "name": "policyAllowed", "type": "boolean" },
          { "name": "score", "type": ["null", "double"], "default": null }
        ] } } },
    { "name": "recommendedOptionId", "type": ["null", "string"], "default": null },
    { "name": "policyRef", "type": { "type": "record", "name": "PolicyRef", "fields": [
        { "name": "policyId", "type": "string" },
        { "name": "version", "type": "int" },
        { "name": "tenantId", "type": "string" }
      ] } },
    { "name": "autonomyTier", "type": "AutonomyTier" },
    { "name": "costBudget", "type": ["null", "io.logisticsos.types.Money"], "default": null },
    { "name": "timeLimitMin", "type": ["null", "int"], "default": null },
    { "name": "deadline", "type": ["null", { "type": "long", "logicalType": "timestamp-millis" }], "default": null },
    { "name": "requestedBy", "type": "string", "doc": "trigger|cascade|human:<id>|reopt" },
    { "name": "requestedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

## 6. Decision events — `orch.decision.v1`

```json
{
  "type": "record",
  "name": "DecisionRequested",
  "namespace": "io.logisticsos.orchestration.v1",
  "fields": [
    { "name": "decisionId", "type": "string" },
    { "name": "caseId", "type": "string" },
    { "name": "decisionType", "type": "DecisionType" },
    { "name": "autonomyTier", "type": "AutonomyTier" },
    { "name": "routedTo", "type": { "type": "enum", "name": "DecisionRoute",
        "symbols": ["AUTO", "HUMAN", "PARTNER"] } },
    { "name": "assignee", "type": ["null", "string"], "default": null },
    { "name": "slaDeadline", "type": ["null", { "type": "long", "logicalType": "timestamp-millis" }], "default": null },
    { "name": "context", "type": "DecisionContext" },
    { "name": "requestedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

```json
{
  "type": "record",
  "name": "DecisionMade",
  "namespace": "io.logisticsos.orchestration.v1",
  "doc": "The auditable outcome. decidedByType distinguishes auto/human/partner.",
  "fields": [
    { "name": "decisionId", "type": "string" },
    { "name": "caseId", "type": "string" },
    { "name": "chosenOptionId", "type": ["null", "string"], "default": null },
    { "name": "decidedByType", "type": { "type": "enum", "name": "DeciderType",
        "symbols": ["AUTO", "HUMAN", "PARTNER"] } },
    { "name": "decidedBy", "type": ["null", "string"], "default": null },
    { "name": "overrodeRecommendation", "type": "boolean" },
    { "name": "rationale", "type": ["null", "string"], "default": null },
    { "name": "policyRef", "type": "PolicyRef" },
    { "name": "decidedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

```json
{
  "type": "record",
  "name": "DecisionDeferred",
  "namespace": "io.logisticsos.orchestration.v1",
  "doc": "Decision postponed pending more info / re-optimization / external signal.",
  "fields": [
    { "name": "decisionId", "type": "string" },
    { "name": "caseId", "type": "string" },
    { "name": "reason", "type": "string" },
    { "name": "reoptimizationId", "type": ["null", "string"], "default": null },
    { "name": "retryAfter", "type": ["null", { "type": "long", "logicalType": "timestamp-millis" }], "default": null },
    { "name": "deferredAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

## 7. Escalation events — `orch.escalation.v1`

```json
{
  "type": "enum",
  "name": "EscalationLevel",
  "namespace": "io.logisticsos.orchestration.v1",
  "symbols": ["L1_AUTOMATED", "L2_SUPERVISOR", "L3_CONTROL_TOWER", "L4_PARTNER_CUSTOMER", "L5_EXECUTIVE"]
}
```

```json
{
  "type": "record",
  "name": "EscalationRaised",
  "namespace": "io.logisticsos.orchestration.v1",
  "fields": [
    { "name": "escalationId", "type": "string" },
    { "name": "caseId", "type": "string" },
    { "name": "decisionId", "type": ["null", "string"], "default": null },
    { "name": "fromLevel", "type": "EscalationLevel" },
    { "name": "toLevel", "type": "EscalationLevel" },
    { "name": "reason", "type": { "type": "enum", "name": "EscalationReason",
        "symbols": ["SLA_BREACH", "LOW_CONFIDENCE", "COST_THRESHOLD", "REGULATED",
                    "REPEATED_FAILURE", "PARTNER_TIMEOUT", "POLICY_FORBIDDEN", "MANUAL"] } },
    { "name": "severity", "type": "Severity" },
    { "name": "assignedTo", "type": ["null", "string"], "default": null },
    { "name": "onCallGroup", "type": ["null", "string"], "default": null },
    { "name": "deadline", "type": ["null", { "type": "long", "logicalType": "timestamp-millis" }], "default": null },
    { "name": "raisedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

```json
{
  "type": "record",
  "name": "EscalationResolved",
  "namespace": "io.logisticsos.orchestration.v1",
  "fields": [
    { "name": "escalationId", "type": "string" },
    { "name": "caseId", "type": "string" },
    { "name": "resolution", "type": "string", "doc": "ACKED_AND_ACTED | DEESCALATED | REASSIGNED | AUTO_RESUMED" },
    { "name": "resolvedBy", "type": ["null", "string"], "default": null },
    { "name": "timeToAckMs", "type": ["null", "long"], "default": null },
    { "name": "resolvedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

## 8. Re-optimization events — `orch.reopt.v1`

```json
{
  "type": "record",
  "name": "ReoptimizationRequested",
  "namespace": "io.logisticsos.orchestration.v1",
  "fields": [
    { "name": "reoptimizationId", "type": "string" },
    { "name": "caseId", "type": ["null", "string"], "default": null },
    { "name": "scope", "type": { "type": "enum", "name": "ReoptScope",
        "symbols": ["SINGLE_SHIPMENT", "LOAD", "WAVE", "NODE", "LANE", "NETWORK"] } },
    { "name": "subject", "type": "SubjectRefs" },
    { "name": "objective", "type": { "type": "enum", "name": "ReoptObjective",
        "symbols": ["MIN_COST", "MIN_TIME", "MAX_SERVICE", "BALANCE_COST_SERVICE", "MIN_EMISSIONS"] } },
    { "name": "priority", "type": "Severity" },
    { "name": "triggerIds", "type": { "type": "array", "items": "string" }, "doc": "coalesced triggers" },
    { "name": "snapshotRef", "type": ["null", "string"], "default": null },
    { "name": "deadline", "type": ["null", { "type": "long", "logicalType": "timestamp-millis" }], "default": null },
    { "name": "requestedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

```json
{
  "type": "record",
  "name": "ReoptimizationCompleted",
  "namespace": "io.logisticsos.orchestration.v1",
  "fields": [
    { "name": "reoptimizationId", "type": "string" },
    { "name": "planId", "type": ["null", "string"], "default": null },
    { "name": "solverId", "type": "string" },
    { "name": "improvementPct", "type": ["null", "double"], "default": null },
    { "name": "recommendationIds", "type": ["null", { "type": "array", "items": "string" }], "default": null },
    { "name": "solveTimeMs", "type": "long" },
    { "name": "completedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

## 9. Orchestration commands — `orch.command.v1`

```json
{
  "type": "enum",
  "name": "CommandType",
  "namespace": "io.logisticsos.orchestration.v1",
  "symbols": ["REALLOCATE_LABOR", "REBOOK_CARRIER", "REROUTE", "REOPTIMIZE", "RELEASE_RESERVATION",
              "RESERVE_INVENTORY", "PRIORITIZE_ORDER", "HOLD_ORDER", "NOTIFY_CUSTOMER",
              "NOTIFY_PARTNER", "ASSIGN_HUMAN", "COMPENSATE", "CLOSE_CASE"]
}
```

```json
{
  "type": "record",
  "name": "OrchestrationCommand",
  "namespace": "io.logisticsos.orchestration.v1",
  "fields": [
    { "name": "commandId", "type": "string" },
    { "name": "commandType", "type": "CommandType" },
    { "name": "targetService", "type": "string", "doc": "tms|wms|yms|oms|notification" },
    { "name": "commandTargetId", "type": "string", "doc": "partition key: aggregate id being acted on" },
    { "name": "caseId", "type": ["null", "string"], "default": null },
    { "name": "decisionId", "type": ["null", "string"], "default": null },
    { "name": "subject", "type": "SubjectRefs" },
    { "name": "params", "type": { "type": "array", "items": {
        "type": "record", "name": "Param", "fields": [
          { "name": "key", "type": "string" },
          { "name": "value", "type": "string" }
        ] } } },
    { "name": "priority", "type": "Severity" },
    { "name": "deadline", "type": ["null", { "type": "long", "logicalType": "timestamp-millis" }], "default": null },
    { "name": "issuedAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
  ]
}
```

> `commandId` is the idempotency key for command execution: target services dedupe on it and must be safe to receive the same command more than once ([common-types.md §4](common-types.md#4-exactly-once--idempotency-contract)).

## 10. Idempotency & exactly-once notes

- **Trigger dedupe/coalesce:** `TriggerEvaluated.dedupeKey` (`type:subject:window`) collapses storms; the Cascade Orchestrator creates at most one open case per `(cascadeTemplate, dedupeKey)` — a second trigger merges into the existing case (`DUPLICATE_MERGED`).
- **One case per subject+template:** case id is deterministic (`hash(template, dedupeKey)`) so replay produces the same case.
- **Decision record is the idempotency anchor:** human/auto decisions write a `DecisionRecord` keyed by `decisionId`; replayed `DecisionMade` events are ignored if a record exists.
- **Commands idempotent by `commandId`;** target services keep a dedupe store.
- **Re-opt coalescing:** requests coalesce per `(scope, subject)` within a window; only the latest survives, with `triggerIds` listing all coalesced causes.
