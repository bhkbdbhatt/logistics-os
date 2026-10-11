# ADR-0022: Experience layer & frontend

- **Status:** Accepted
- **Date:** 2026-10-11
- **Related:** [../services.md](../services.md) §L6 · [0003-cqrs-read-models.md](0003-cqrs-read-models.md) · [0020-identity-authentication-authorization.md](0020-identity-authentication-authorization.md) · [../analytics/control-tower.md](../analytics/control-tower.md)

## Context

The platform is headless: it exposes JSON control-tower/read-model/BFF endpoints and Kafka topics, but no user interface. L6 in [services.md](../services.md) defines **BFF-per-surface** (`bff-ops`, `bff-customer`, `bff-partner`, `bff-mobile`) with **no domain logic**, composed from `*_view` reads + orchestrator commands, pushing live updates via WS/SSE. We need a first operator-facing surface.

## Decision

1. **React + TypeScript + Vite** single-page app, built to static assets and served by **nginx** (container `ghcr.io/logistics-os/web`). Lives in `web/` (own npm project), built in CI, deployed to k8s.
2. **The SPA talks only to BFFs** (`bff-ops` first). It never calls an L3/L5 service or a database directly — the L6 rule holds (ADR-0003, services.md §3.2).
3. **Auth = OIDC Authorization Code + PKCE** against Keycloak (ADR-0020); access/refresh tokens kept **in memory** (not `localStorage`), silent renew; a `local` dev login fallback mirrors the backend's local-JSON mode so the UI runs without an IdP.
4. **First surface = Ops console** over `bff-ops`: dashboard (tower/read-model health + counts), shipment list + detail, alert queue, one-click actions (`notify-customer`, gated on `operator`), and an **edition/entitlement badge** (`GET /tower/entitlement`, ADR/licensing).
5. **Live updates deferred**: the BFF stays request/response in this slice; SSE/WebSocket push is a follow-up (keeps the backend unchanged for now).
6. **Deterministic dev proxy**: Vite dev server proxies `/ops`, `/tower`, `/auth` to the local BFF/tower so the SPA is origin-clean in dev; nginx does the same in the container.

## Consequences

**Positive**
- A clickable product for demos/sales (aligns with the editions/packaging work) without violating the BFF boundary.
- One SPA build, reused by all customer overlays (branding via runtime config, not rebuild).
- In-memory tokens + PKCE avoid the common SPA token-exfiltration pitfalls.

**Negative**
- Adds a Node toolchain + CI job and a static-site deployment; mitigated by a self-contained `web/` project and a single image.
- No offline/PWA or SSR (not needed for an internal ops console).

## Alternatives considered

- **Server-rendered Thymeleaf/HTMX in the BFF** — rejected: weaker for the live dashboard/filtering UX and entangles UI with the BFF build.
- **Vue** — viable; React chosen for ecosystem/team velocity (recorded preference).
- **SPA calling services directly with per-service CORS** — rejected: violates the BFF rule and multiplies auth/CORS surface.
- **localhost:localStorage tokens** — rejected: XSS-exfiltration risk; in-memory + PKCE is safer.
