# Licensing & Entitlements

> Status: Accepted (soft enforcement) · Related: [editions.md](editions.md) · [../../editions/](../../editions/README.md) · [../adr/0006-multi-tenant-isolation-model.md](../adr/0006-multi-tenant-isolation-model.md)

## Model

A customer is licensed to run a specific **edition@version** for a **tenant**, subject to limits
(expiry, seats, throughput) and a feature set. The license is the contract; entitlement is its
technical projection.

```
License (signed, per customer)  →  Entitlement (edition, version, features, limits, expiry, tenant)
                                 →  Feature gates at runtime  →  platform:entitlement
```

## Enforcement phases

We ship **soft** first and earn our way to **hard**. The seam is `platform:entitlement`, so the
hard phase is additive.

| Phase | What it does | Status |
|-------|--------------|--------|
| **Soft** | No license required. Edition feature flags resolved from config/edition; a missing license logs a notice and runs in a permissive *evaluation* mode. Contractual terms govern. | **current** |
| **Hard** | A **signed license** (Ed25519/JWT) is required; `platform:entitlement` verifies signature + expiry + tenant binding and enforces feature gates, failing safe (degrade gated features) when invalid. | later |

### Soft-phase behaviour

- `ENTITLEMENT_LICENSE` unset → edition defaults to `evaluation` with all technically-available
  features on; `GET /entitlement` reports `mode: evaluation`.
- `ENTITLEMENT_LICENSE` present and parseable → the declared edition/features are used.
- Feature flags are read through `EntitlementGate.isEnabled(feature)`; services must never read the
  raw flag, so switching to hard enforcement is a one-place change.

## License file

Hard-phase license (reserved shape; soft phase only reads `edition`/`features`):

```json
{
  "licenseId": "LIC-2026-0001",
  "tenant": "acme",
  "edition": "enterprise",
  "version": "1.0.0",
  "issuedAt": "2026-01-01T00:00:00Z",
  "expiresAt": "2027-01-01T00:00:00Z",
  "features": { "l5_orchestration": true, "sso_saml": true },
  "signature": "base64(ed25519(...))"
}
```

## Disallowed

- No phone-home/licence-server dependency for core operation (self-hosted must run air-gapped).
- No PII in the license; customer identity stays in `tenant`.
- Secrets never in code or events ([cross-cutting.md §4](../cross-cutting.md)).

## Legal

Commercial terms are governed by the EULA ([../../LICENSE](../../LICENSE)); third-party components
are disclosed in [../../THIRD-PARTY-NOTICES.md](../../THIRD-PARTY-NOTICES.md), generated from the
release SBOM.
