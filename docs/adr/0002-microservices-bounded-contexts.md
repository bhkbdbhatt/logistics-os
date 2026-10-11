# ADR-0002: Microservices decomposed by bounded context

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../architecture.md](../architecture.md) · [../services.md](../services.md)

## Context

The logistics domain splits naturally into subdomains with different rates of change, scaling profiles, and (in several cases) different COTS vendors. A single monolith would force a shared data model and one release cadence across warehouse, transport, and finance — all of which change at very different speeds and are often owned by different teams.

## Decision

Decompose by **bounded context** aligned to logistics domains: OMS, TMS, WMS, YMS, Visibility, Billing, plus platform services (MDM, read models, orchestrator, intelligence). Each service owns its data and API, publishes via outbox, and integrates only through the backbone. Services are grouped into architectural layers (observation → data → execution → intelligence → orchestration → experience → analytics).

## Consequences

**Positive**
- Independent deploy/scale; clear ownership; COTS can implement a context behind an ACL.
- Failure isolation via bulkheads.

**Negative**
- Distributed-system complexity: sagas, eventual consistency, observability needed.
- More infra and CI/CD surface.

**Mitigations**
- Platform services (backbone, read-model builder, policy) absorb cross-cutting complexity; ADR-0004 and ADR-0007 define consistency and publishing.

## Alternatives considered

- **Modular monolith** — viable for a startup; rejected at mid-to-large scale where 3PL/4PL spans many nodes, tenants, and vendor apps.
- **Domain monolith per layer** — rejected: still couples unrelated domains.
