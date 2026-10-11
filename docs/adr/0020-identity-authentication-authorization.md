# ADR-0020: Identity, authentication & authorization

- **Status:** Accepted
- **Date:** 2026-10-11
- **Related:** [../cross-cutting.md](../cross-cutting.md) §4 · [0006-multi-tenant-isolation-model.md](0006-multi-tenant-isolation-model.md) · [0022-experience-layer-and-frontend.md](0022-experience-layer-and-frontend.md) · [../product/licensing.md](../product/licensing.md)

## Context

[cross-cutting.md §4](../cross-cutting.md) mandates OIDC/OAuth2 IAM from a bought provider (Okta/Entra), gateway-terminated auth, mTLS between services, secrets in a vault, and **`tenantId` mandatory in every token and row** (ADR-0006). The Phase-1 scaffold implements none of this: services expose open JSON APIs, tenant is an unauthenticated `X-Tenant-Id` header, and `platform:tenant` (`TenantId`/`TenantContext`) is unused.

We must add real identity/authorization while preserving:
- **Self-hosted / air-gapped** operation (editions can be sold self-hosted; the licensing model forbids a mandatory phone-home — [licensing.md](../product/licensing.md)),
- **Offline/local dev** without standing up an IdP,
- Portability to external providers (Entra/Okta) for SaaS.

## Decision

1. **OAuth2 / OIDC** is the single auth mechanism. Services are **OAuth2 resource servers**; the browser is an **Authorization Code + PKCE** client; machine clients use client-credentials.
2. **Keycloak is the reference IdP** — self-hostable, air-gap friendly, and already listed in [ADR-0019](0019-repository-and-build-runtime.md) / THIRD-PARTY-NOTICES. External Entra/Okta are supported purely by configuration (`issuer-uri`, audience, claims).
3. **`platform:security`** provides the reusable Spring wiring (auto-configured): JWT decoding/validation, claim→authority mapping, tenant resolution, and a servlet filter that binds `TenantContext` for the request and clears it after.
4. **Tenant is a required claim** (`tenant_id`). Services trust the **token**, never a client-supplied `X-Tenant-Id`. The filter overwrites/derives the header from the claim; requests without a tenant claim are rejected (401/403). Row-level isolation is by `tenant_id` (ADR-0006 logical tier).
5. **Roles** (realm roles) and **scopes** (client) both map to authorities. Baseline realm roles: `viewer` (read), `operator` (one-click ops actions), `admin` (maintenance). Endpoints use method security (`@PreAuthorize("hasRole('OPERATOR')")`), e.g. `POST /ops/actions/*` and `POST /tower/actions/*` require `operator`.
6. **Local-JSON mode** (`logistics.security.mode=local`): for dev/CI/offline, users live in a JSON file (`users.json`: username, password hash, tenant, roles). A dev-only `POST /auth/login` issues an **HS256** JWT signed with a configured secret; the same resource-server rules apply. This is explicitly **not for production** and is disabled when `mode=oidc`.
7. **Secrets** (JWT client secrets, local signing secret, DB credentials) come from env/vault, never code (cross-cutting §4).

## Consequences

**Positive**
- One identity model across SaaS and self-hosted; swapping IdP is config, not code.
- The unused `platform:tenant` becomes load-bearing: ephemeral `TenantContext` bound at the edge, consumed by services/persistence.
- Offline/CI can exercise the full authz path without Keycloak (local-JSON mode), so tests and demos stay hermetic.

**Negative**
- Every service gains a security dependency and a startup requirement (a reachable issuer in `oidc` mode); mitigated by lazy JWKS and the local mode.
- Local-JSON passwords/tokens are a deliberate dev-only compromise; mitigated by gating on `mode` and a loud startup warning.
- Tenant claim is now a hard contract; token issuance must always include it (realm mapper / local issuer).

## Alternatives considered

- **API gateway only, services unauthenticated** — rejected: violates defense-in-depth (cross-cutting §4 service identities) and would re-open tenant trust holes.
- **Spring Authorization Server (build our own IdP)** — rejected: security surface we should buy, not build; Keycloak is free, mature, and self-hostable.
- **Signed API keys / HMAC per tenant** — rejected: no standard OIDC claims, weak rotation, poor fit for browser SSO.
- **mTLS-only service identity (no user tokens)** — insufficient for user-facing surfaces; kept as a future mesh concern, not a substitute.
