# ADR-0008: Schema Registry and event versioning

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../events.md](../events.md) · [../cross-cutting.md](../cross-cutting.md)

## Context

Dozens of services and many COTS integrators produce and consume events on shared topics. Without a governed contract, producers silently break consumers. The domain is standardized and externally regulated (EDI 214/210, financial records), so events must evolve predictably over years.

## Decision

Use a **Schema Registry** with Avro or Protobuf. Enforce **backward compatibility** for all schema changes in CI (consumer-driven contract tests for critical pairs). Schema changes within a topic are **additive only**. Any breaking change requires a **new major topic** (`.v2`) published alongside the old one during a dual-write/dual-read migration window, and a migration ADR. Deprecation of an old major is scheduled and announced.

## Consequences

**Positive**
- Safe independent deploys; predictable evolution; tooling catches incompatibilities pre-merge.
- Auditable contract history.

**Negative**
- Extra CI gate; dual-write/dual-read complexity during major migrations.
- Registry is a critical dependency (must be HA).

**Mitigations**
- Registry in HA config; migration playbook; contract tests in CI.

## Alternatives considered

- **Schemaless JSON** — rejected: no guarantees, silent breakage.
- **Only forward/transitive compatibility** — rejected: too permissive for producers to break consumers.
- **In-place breaking changes** — rejected: violates independent deployability.
