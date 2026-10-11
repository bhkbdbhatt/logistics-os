# ADR-0009: Canonical model as the data moat

- **Status:** Accepted
- **Date:** 2026-10-10
- **Related:** [../data-model.md](../data-model.md) · [../architecture.md](../architecture.md)

## Context

The platform integrates multiple COTS execution systems and external partners, each with its own model. If their models leak across boundaries, the platform becomes a patchwork: cross-domain analytics, orchestration, and tenant onboarding all degrade. A 3PL/4PL's durable advantage is a unified, well-governed view of orders, inventory, shipments, and economics across its network.

## Decision

Define and govern a **canonical data model** owned by the Data layer and enforced by MDM + Schema Registry. Execution systems may keep richer internal models, but **only canonical events/objects cross service and layer boundaries**. External/COTS structures are mapped through anti-corruption adapters. Master data (parties, locations, nodes, items, lanes, SLAs) has single-writer ownership via MDM.

## Consequences

**Positive**
- Cross-domain analytics, orchestration, and onboarding ride on a stable vocabulary.
- Vendor swap becomes feasible (swap implementation, preserve canonical contract).
- Enables the digital twin and consistent metrics.

**Negative**
- Mapping/ACL effort; governance overhead; occasional impedance with vendor models.

**Mitigations**
- ACL tooling; additive schema evolution ([ADR-0008](0008-schema-registry-and-versioning.md)); ownership table enforced in review.

## Alternatives considered

- **Adopt a vendor's model as canonical** — rejected: locks in a vendor, biases the model, blocks multi-vendor.
- **No canonical model (map pair-to-pair)** — rejected: N×N mappings, inconsistent analytics.
