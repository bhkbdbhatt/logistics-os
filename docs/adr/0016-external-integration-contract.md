# ADR-0016: External integration contract — async-by-default, keyed idempotency, explicit ownership

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../integration-architecture.md](../integration-architecture.md) · [../integrations.md](../integrations.md) · [ADR-0007](0007-outbox-and-idempotency.md) · [ADR-0010](0010-cots-strangler-and-anti-corruption.md)

## Context

The platform connects to systems with very different capabilities and failure profiles: an ERP financial backbone (SAP/Oracle), OMS (ours or external), parcel and LTL carrier APIs (DHL/FedEx/UPS/regional), warehouse automation via WCS (conveyors/AGVs), offline-capable driver/worker mobile apps, and customer-facing tracking. Treating them uniformly fails: some must be exactly-once (money, inventory), some tolerate loss (telemetry), some demand sub-100ms handshakes (WCS), and some are occasionally-connected (mobile). Without a shared contract we risk duplicate financial postings, duplicate tenders/labels, lost PODs, and unclear data ownership.

## Decision

Adopt a single external-integration contract across all adapters:

1. **Async by default; sync only when the caller needs the answer now.** Sync is reserved for read-your-write UX, blocking pricing/labeling/credit checks, and low-latency WCS handshakes. Everything else is event-driven through Kafka behind an ACL ([ADR-0010](0010-cots-strangler-and-anti-corruption.md)).
2. **Keyed idempotency on every mutation; dedupe on every inbound feed.** Outbound calls carry an idempotency key; an external-mapping table links our aggregate ids to vendor object ids for reconciliation. Non-idempotent calls are never retried blindly — use keyed retry or query-before-create ([ADR-0007](0007-outbox-and-idempotency.md)).
3. **Explicit, single system of record per fact.** When we are not the SoR we never overwrite the owner; when we are, peers consume our events.
4. **Reliability tiers by blast radius** — Tier 0 money/inventory (must not lose, exactly-once effects) through Tier 3 analytics (best-effort), each with its own retry/dedupe posture.
5. **Contract style follows the peer.** We adapt to REST/gRPC/EDI/webhook/streaming/CDC; the canonical contract stays clean and vendor-neutral.
6. **Isolate dependencies** with per-peer circuit breakers, bulkheads, and rate-limit/quota controls.

## Consequences

**Positive**
- No duplicate financial postings, tenders, labels, or inventory effects.
- A carrier or ERP outage degrades only its own lane; execution continues.
- Uniform operational model (outbox, DLQ, reconciliation, idempotency library).
- Clear ownership prevents write conflicts and leaks of vendor models into the canonical model.

**Negative**
- Every adapter must implement idempotency, mapping, and retry discipline (non-optional).
- Async-by-default adds eventual-consistency handling to UIs and partial-failure UX.
- External-mapping tables and reconciliation jobs are additional operational surface.

**Mitigations**
- Shared adapter framework (idempotency, backoff, breaker, DLQ, mapping) so peers reuse one implementation.
- Contract tests per integration; reconciliation runbooks per Tier-0/1 peer.
- Sync allowance is explicit and narrow; everything else defaults to events.

## Alternatives considered

- **Sync-everything** — rejected: couples availability, amplifies fan-out, blocks on slow peers.
- **Async-everything** — rejected: some operations (label, credit check, WCS handshake) genuinely need an immediate answer.
- **Blind retries** — rejected: duplicate labels/charges/pickups.
- **Let each integration define its own conventions** — rejected: unmanageable inconsistency and audit gaps.
