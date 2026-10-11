# ADR-0006: Multi-tenant isolation model

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../cross-cutting.md](../cross-cutting.md) · [../data-model.md](../data-model.md)

## Context

A 3PL/4PL serves many shippers/customers, plus carriers and 3PL partners. Some tenants are small and price-sensitive; a few are large and demand isolation, dedicated throughput, or data residency. We must provide strong tenant isolation and data residency without multiplying infrastructure cost for every small tenant.

## Decision

Adopt **logical isolation by default** — `tenantId` is mandatory in every event envelope, token, and row (row-level security), with shared topics/clusters and per-tenant quotas. Offer **dedicated isolation** (separate topics/namespaces, schemas, or clusters) for large or regulated tenants. Enforce **data residency** by pinning tenants to a region with gateway-level routing. All access is audited on `audit.*`.

## Consequences

**Positive**
- Cost-efficient for the long tail; strong isolation where it matters.
- Residency and compliance achievable per tenant.

**Negative**
- Shared clusters need noisy-neighbor controls; two isolation tiers to operate.
- Cross-tenant analytics must respect residency and consent.

**Mitigations**
- Per-tenant quotas/rate limits; region-pinned data planes; tenancy enforced in platform primitives (gateway, schema, RLS), not per-service ad hoc.

## Alternatives considered

- **Single tenant per deployment** — rejected: cost-prohibitive for the tail.
- **Pure shared (no isolation option)** — rejected: fails large/regulated tenant requirements.
- **Database-per-tenant** — viable hybrid, but costly at scale; kept as a tier-3 option.
