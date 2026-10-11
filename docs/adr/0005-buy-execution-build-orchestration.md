# ADR-0005: Buy execution, build orchestration

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../build-vs-buy.md](../build-vs-buy.md) · [../services.md](../services.md)

## Context

The platform needs TMS, WMS, YMS, OMS, visibility, and billing, plus a differentiating layer (ATP/promise, routing/consolidation, control tower, tenant policy). Building every execution product from scratch is slow and capital-intensive; buying the differentiating logic from a vendor bakes in a generic algorithm and erodes the moat. A clear stance on build-vs-buy is needed per component.

## Decision

**Buy** commodity, regulated, and fast-moving-standards execution (WMS/TMS/YMS cores, visibility network, EDI translation, IAM, lakehouse, observability, commodity solvers) and wrap them with anti-corruption layers. **Build** the differentiating layer you own: orchestrator/flows, ATP/promise, control tower/exception management, multi-tenant policy/isolation, and the canonical data model. For mixed components, **buy the core and extend at the edges**.

## Consequences

**Positive**
- Fast time-to-value on execution; investment concentrated on the moat.
- Vendor maintenance for fast-moving standards (EDI, carrier networks).

**Negative**
- Integration/vendor-management overhead; risk of vendor lock-in.
- Requires disciplined ACLs to keep vendor models out of L2/L5/L7.

**Mitigations**
- ACLs + strangler-fig ([ADR-0010](0010-cots-strangler-and-anti-corruption.md)); canonical model ([ADR-0009](0009-canonical-model-as-moat.md)); scoring rubric in build-vs-buy.md.

## Alternatives considered

- **Build everything** — rejected: slow, capital-heavy, no moat from edge software.
- **Buy everything (incl. orchestration)** — rejected: surrenders the core differentiator and flexibility for tenant-specific operations.
