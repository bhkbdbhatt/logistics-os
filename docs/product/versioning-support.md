# Versioning & Support

> Status: Accepted · Related: [editions.md](editions.md) · [packaging-delivery.md](packaging-delivery.md) · [../../editions/versions.yaml](../../editions/versions.yaml) · [../release-plan.md](../release-plan.md)

## Versioning

- Each **edition@version** uses **semver** (`MAJOR.MINOR.PATCH`), tracked in
  [`editions/versions.yaml`](../../editions/versions.yaml).
- The repo build carries one `version` (Gradle); the **edition version** is the commercial line and
  may advance independently per edition.
- **Schema/event compatibility is decoupled from editions**: canonical topics are versioned by their
  own `.vN` and governed by [ADR-0008](../adr/0008-schema-registry-and-versioning.md). An edition
  version bump never implies a breaking contract change.

| Change | Edition version |
|--------|-----------------|
| Backward-compatible feature / fix | PATCH or MINOR |
| New service or feature flag in an edition | MINOR |
| Removed/renamed service, entitlement semantics change | MAJOR |

## Channels

| Channel | Use | Policy |
|---------|-----|--------|
| `stable` | all editions | recommended for production |
| `lts` | Enterprise | 24-month security + regression fixes |
| `beta` | opt-in | pre-release, no SLA |

## Support windows

- **Standard** — community; current version only.
- **Professional** — current + one previous MINOR.
- **Enterprise** — `lts` line supported for **24 months** from its GA date; current + previous MINOR.

## Upgrade path

- Editions are upgraded in place via a new image set; no data migration for MINOR/PATCH (events are
  append-only, projections rebuildable — [ADR-0003](../adr/0003-cqrs-read-models.md)).
- MAJOR upgrades ship a migration note + dual-write/dual-read plan when topics change.
- Rollback = redeploy the previous immutable image digests (see [packaging-delivery.md](packaging-delivery.md)).

## Release cut

At each cut: set the repo `version`, update `editions/versions.yaml`, run
[`.github/workflows/release.yml`](../../.github/workflows/release.yml) for the target edition, and
record image digests in the release notes under [`docs/releases/`](../releases/).
