# Packaging & Delivery

> Status: Accepted · Related: [editions.md](editions.md) · [licensing.md](licensing.md) · [versioning-support.md](versioning-support.md) · [../engineering-build.md](../engineering-build.md) · [../../ops/k8s/README.md](../../ops/k8s/README.md)

Logistics OS is delivered **both** ways: as a **hosted SaaS** (we operate one multi-tenant platform)
and as **self-hosted releases** (a customer runs a version in their own cloud). Both consume the same
versioned artifacts.

## Artifacts per edition@version

| Artifact | Where | Notes |
|----------|-------|-------|
| **Container images** | `ghcr.io/logistics-os/<svc>:<edition>-<version>` | signed (cosign) + SBOM + provenance; subset per edition |
| **Deployment bundle** | `ops/k8s/overlays/<customer>` (kustomize) | base + edition + customer overlay; dedicated namespace |
| **Edition manifest** | `editions/<name>.yaml` + `versions.yaml` | service subset + features + version |
| **License** | `ENTITLEMENT_LICENSE` (Secret/ConfigMap) | soft today; signed license later |
| **Legal** | `LICENSE` (EULA) + `THIRD-PARTY-NOTICES.md` | attached to the GitHub release |

## Hosted SaaS

- One cluster, per-tenant logical isolation (`tenantId` in every envelope/token/row — [ADR-0006](../adr/0006-multi-tenant-isolation-model.md)).
- Edition features resolved per tenant via `platform:entitlement`.
- Upgrades are operator-driven on the `stable`/`lts` channels; customers never run pipelines.

## Self-hosted release

1. Operator builds/signs images via [`release.yml`](../../.github/workflows/release.yml) for the
   purchased `edition@version` and pushes to the customer's registry.
2. Customer applies the overlay: `kubectl apply -k ops/k8s/overlays/<customer>` (dedicated namespace).
3. Customer supplies `ENTITLEMENT_LICENSE`; the platform reports its edition/features via
   `GET /entitlement`.
4. Verified with the [`kind` smoke](../../ops/kind/README.md) before hand-off.

## Verification & trust

- **Images**: cosign-signed; SBOM + provenance attestations (buildx).
- **Bundle**: `kubectl kustomize ops/k8s/overlays/<customer>` output is diffable and reviewable.
- **Smoke**: `smoke.yml` (manual + nightly) proves the edition boots and moves an event end-to-end.

## Distribution

- Private GHCR packages per edition tag; customer-scoped read access.
- Source is **not** distributed; only signed images + the deployment bundle + license/EULA.

## Status

This is a **commercial-preview** slice: the catalog, docs, entitlement seam, overlays, and release
pipeline are in place; signed-license enforcement and a per-customer registry/provisioning service
are follow-ups. See [../../docs/release-plan-0.2.0.md](../release-plan-0.2.0.md) §7 and the roadmap
Phase 3 (multi-tenant/4PL onboarding).
