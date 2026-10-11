# ADR-0010: COTS strangler-fig + anti-corruption layer

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../integrations.md](../integrations.md) · [../build-vs-buy.md](../build-vs-buy.md) · [ADR-0005](0005-buy-execution-build-orchestration.md)

## Context

Per [ADR-0005](0005-buy-execution-build-orchestration.md) we buy execution systems (WMS/TMS/YMS/OMS/visibility) and may later replace or augment them. Vendor databases and APIs expose vendor-specific models; direct access from other services would couple the platform to a vendor and prevent replacement. Legacy systems may need graceful replacement while running.

## Decision

Wrap every COTS/legacy system with an **anti-corruption layer** that translates between the vendor model and the canonical model, exposing **commands consumed** and **canonical events published**. Never allow another service to query a vendor DB directly. Where replacing a legacy system, apply the **strangler-fig** pattern: route a growing share of traffic through the new implementation behind the ACL until the legacy system is retired, keeping the canonical contract stable throughout.

## Consequences

**Positive**
- Vendor swap without downstream changes; stable contract across replacement.
- Incremental, low-risk migration; no big-bang cutover.
- Keeps vendor models out of L2/L5/L7 (protects [ADR-0009](0009-canonical-model-as-moat.md)).

**Negative**
- ACL development and maintenance; dual-run cost during strangling.

**Mitigations**
- Reusable translation framework; CDC + event mapping; feature-flagged traffic routing at the ACL.

## Alternatives considered

- **Direct vendor DB access** — rejected: coupling, no replacement path, leaks vendor model.
- **Big-bang replacement** — rejected: high risk, no rollback.
- **Accept vendor model as canonical** — rejected: see [ADR-0009](0009-canonical-model-as-moat.md).
