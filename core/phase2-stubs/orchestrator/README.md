# phase2-stubs/orchestrator

Minimal **L5 command stub** for the Phase-2 orchestrator surface. It proves the command path
(`orch.command.v1`) boots and produces valid, consumable commands without yet implementing the
real orchestration engine.

Scope (per [docs/engineering-build.md](../../../docs/engineering-build.md) and
[docs/release-plan-0.2.0.md](../../../docs/release-plan-0.2.0.md) M1):

- Builds idempotent `io.logisticsos.orchestration.v1.OrchestrationCommand` payloads
  (`NOTIFY_CUSTOMER`, target `notification`) via `StubCommandBuilder`.
- Emits them on `orch.command.v1`, keyed by shipment id:
  - scheduled heartbeat when `orchestrator.emit-enabled=true`, or
  - manually via `POST /orch/commands` with `{"shipmentId":"SH-1","tenantId":"acme","caseId":"case-9"}`.
- Bundles the same contract schemas (envelope + types + orchestration v1) the control-tower uses, so
  it runs locally with `schema.resource=true` and no Schema Registry.

Explicitly **out of scope**: workflow engine, sagas/compensations, trigger + exception cascade,
decision router, promise/ATP. That is the real orchestrator in Phase 2 (0.3.0+)
([docs/orchestration/README.md](../../../docs/orchestration/README.md)).

Run: `gradle :core:phase2-stubs:orchestrator:bootRun` (port 8090). Tests: unit
(`StubCommandBuilderTest`) + embedded-Kafka IT (`CommandEmitIT`).