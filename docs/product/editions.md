# Product Editions

> Status: Accepted · Related: [../../editions/README.md](../../editions/README.md) · [licensing.md](licensing.md) · [versioning-support.md](versioning-support.md) · [packaging-delivery.md](packaging-delivery.md) · [../adr/0006-multi-tenant-isolation-model.md](../adr/0006-multi-tenant-isolation-model.md)

Logistics OS is sold as **editions** — named feature bundles with a support tier and a semver line.
The machine-readable definitions live in [`editions/`](../../editions/README.md); this page is the
human-facing summary.

## SKUs

| Edition | Tier | Theme | Support | Delivers |
|---------|------|-------|---------|----------|
| **Standard** | 1 | Connect & observe | community | Canonical model, CQRS read models, MDM, read-only control tower, single tenant |
| **Professional** | 2 | Decide & act | standard | + ops BFF (one-click actions), per-tenant quotas, DLQ/replay tooling |
| **Enterprise** | 3 | Optimize & scale | premium | + L5 orchestration, dedicated isolation, SSO/SAML, 7-year audit retention, analytics exports |

Each edition also exists as a **per-customer branded variant** (e.g. "DHL Edition") = an edition +
a customer overlay (branding, config, residency) in a dedicated namespace. Customer-branded variants
are *config*, not forks — one codebase.

## What an edition controls

1. **Deployed service subset** — which services run (see `services:` in each `editions/<name>.yaml`).
2. **Feature flags / entitlements** — runtime flags (e.g. `l5_orchestration`, `ops_actions`) resolved
   by [`platform:entitlement`](../../platform/entitlement) and, later, by a signed license.
3. **Support tier** and **channels** (stable / lts / beta) — see [versioning-support.md](versioning-support.md).

## Packaging rule

A single build of the codebase is tagged per edition@version. An edition never forks source; the
edition is expressed as **service subset + feature flags + branding**, so a bug fix lands once and is
re-released to every edition that includes the affected service.

## Relationship to tenancy

Editions are a **commercial** axis; tenancy ([ADR-0006](../adr/0006-multi-tenant-isolation-model.md))
is an **isolation** axis. Standard/Professional default to logical isolation (shared cluster,
mandatory `tenantId`); Enterprise may use dedicated isolation (namespace/cluster per tenant). The
two compose: a customer buys `enterprise@1.0.0` **and** chooses an isolation tier.
