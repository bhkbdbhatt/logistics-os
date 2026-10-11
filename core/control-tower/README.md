# control-tower (L5/L6, read-mostly MVP)

Real-time operator cockpit: **live ops state + alert queue + one-click actions**
(`docs/analytics/control-tower.md`, `docs/roadmap.md` Phase 1 #7).

## Behaviour

- **Hot store** — `TowerStatePoller` keeps an in-process cache in sync with the compacted CQRS state
  topics (`shipment.state.v1`, `inventory.state.v1`) emitted by read-model-builder. The REST tier
  reads only this cache (no Streams/Kafka access, no L3 DB — ADR-0003).
- **Alert queue** — `AlertRuleEngine` re-evaluates every few seconds: `EXCEPTION` shipments (HIGH)
  and in-flight shipments idle past the SLA threshold (`SLA_AT_RISK`; CRITICAL past 2×). Alerts are
  de-duplicated by `subjectType:subjectId:type`; cleared conditions are RESOLVED (kept for audit),
  reopened alerts get a fresh `raisedAt`. (These are UI-facing operational alerts — the orchestrator
  evaluates the same conditions as planning triggers, correlated but not duplicated.)
- **One-click actions** — `POST /tower/actions/notify-customer` emits an **idempotent**
  `orch.command.v1` command (`OrchestrationCommand`, `commandId` = ULID idempotency key,
  `correlationId = shipment:<id>`, targeted service `notification`). Policy/DecisionRecord recording
  is Phase-2 (`docs/orchestration/`).

## API

- `GET /tower/shipments?status=` · `GET /tower/shipments/{id}` — live map surface
- `GET /tower/inventory`
- `GET /tower/alerts?status=open|resolved`
- `POST /tower/actions/notify-customer` `{ "shipmentId": "...", "caseId": "..." }`
- `GET /tower/health`

## Run

```
gradle :core:control-tower:bootRun     # port 8087
```

`KAFKA_BOOTSTRAP_SERVERS` must reach the same broker read-model-builder writes state topics to.

## Tests

- `AlertRuleEngineTest` — rule matrix (EXCEPTION / SLA_AT_RISK / CRITICAL / terminal / fresh),
  de-dup + resolution + raisedAt semantics via `TowerCache.applyAlerts`.
- `CommandBuilderTest` — envelope fields + `OrchestrationCommand` payload decode.