# Release Plan — 0.3.0 "Experience, identity & persistence"

> Status: Proposed · Target branch: `main` · Owner: docs/backed by repo admin
> Builds on 0.2.0 (images + kind smoke + MSK). 0.3.0 makes the platform **usable and durable**:
> a real UI, real identity, and the first real database with a transactional outbox.

## 1. Goal

Turn the headless, in-memory scaffold into something an operator can log into and use, backed by
durable state and real auth — without breaking the accepted architecture (BFF-only UI, per-service DB,
outbox for writes, CQRS read models stay log-sourced).

## 2. Scope

**In**
- **Identity & access (ADR-0020).** `platform:security`: OAuth2 resource-server wiring, claim→authority
  mapping, `TenantContext` binding filter; **Keycloak** reference IdP + **local-JSON** offline mode.
  Roles `viewer`/`operator`/`admin`; tenant is a required token claim. Services trust the token, not
  `X-Tenant-Id`.
- **Persistence (ADR-0021).** `platform:persistence` (`JdbcOutboxRepository`, `JdbcDedupeStore`,
  shared migrations); **MDM** golden records become a **Postgres system-of-record** with Flyway;
  MDM writes publish via the **transactional outbox**; `ingest-gateway` dedupe becomes Postgres-backed.
  Read models stay Kafka-sourced (no view DB).
- **Experience (ADR-0022).** `web/`: **React + TS + Vite** ops console over `bff-ops` (bff untouched
  in shape): dashboard, shipments, alerts, one-click actions, entitlement badge; OIDC PKCE + local login.
- **Local stack.** Compose adds **PostgreSQL 16** + **Keycloak** (realm import JSON) + the web dev proxy.
- **Deploy.** k8s manifests for Postgres, Keycloak, `web`; overlays carry auth/web config; image
  workflow builds `ghcr.io/logistics-os/web`.
- ADRs 0020–0022 + this plan + updated `engineering-build.md` / README / `docs/releases/0.3.0.md`.

**Out (deferred → 0.4.0)**
- Full L5 orchestration engine; ATP/promise; intelligence; analytics T0–T3.
- SSE/WebSocket live push from BFFs; customer/partner/mobile BFFs + their SPAs.
- Persisting projections; multi-region; mTLS mesh; secrets vault integration.

## 3. Definition of Done (exit criteria)

- [ ] **M1 `platform:persistence`**: JDBC outbox + dedupe stores pass unit tests (H2 pg-mode); shared
      migrations present; `JdbcOutboxRepository.save` is duplicate-safe (`UNIQUE(dedupe_key)`).
- [ ] **M2 `platform:security`**: JWT resource-server + tenant filter; `local` mode issues/validates
      tokens from `users.json`; tests cover tenant binding + role enforcement.
- [ ] **M3 Services secured**: `mdm`, `control-tower`, `bff-ops`, `ingest-gateway` require a token;
      `operator` required for action endpoints; tenant comes from the token.
- [ ] **M4 MDM durable + outbox**: golden records survive restart; upsert writes record+outbox in one
      tx; a scheduled publisher emits `mdm.*.events.v1`; `MdmPublishIT` green.
- [ ] **M5 Ops console**: `web/` builds + lints in CI; dashboard/shipments/alerts/actions work against
      `bff-ops`; entitlement badge reflects the overlay license; local login works without Keycloak.
- [ ] **M6 Local stack**: `docker compose up` yields Kafka + Postgres + Keycloak (+ web dev); realm
      imported; documented in `ops/local-compose/README.md`.
- [ ] **M7 Deploy**: k8s manifests + overlays for Postgres/Keycloak/web; `images.yml` builds `web`;
      `kind` smoke updated; docs updated.
- [ ] Release notes `docs/releases/0.3.0.md` published; version bumped; tag `v0.3.0`.

## 4. Milestones & gates

| # | Milestone | Work | Gate | Est. |
|---|-----------|------|------|------|
| M1 | Persistence lib | `platform:persistence` JDBC stores + shared Flyway SQL; H2 pg-mode tests | unit tests green | 1–2 d |
| M2 | Security lib | `platform:security` OIDC + local-JSON modes; tenant filter | tests green; local login works | 2–3 d |
| M3 | Service auth | wire lib into mdm/control-tower/bff-ops/ingest-gateway; `@PreAuthorize` | 401 without token; 403 role-gated | 1–2 d |
| M4 | MDM SoR + outbox | `MdmStore`/`JdbcMdmStore`; Flyway; outbox write+publisher | restart persists; events emitted | 2–3 d |
| M5 | Ops console | `web/` SPA + auth + pages; CI build/lint | builds in CI; manual E2E vs bff | 3–4 d |
| M6 | Local stack | compose Postgres/Keycloak/web; realm JSON | one-command dev boot | 1 d |
| M7 | Deploy + docs | k8s + overlays + web image; docs/release notes | manifests valid; smoke green | 2–3 d |

**Sequence:** M1 → M2 → M3 → M4 → M5 → M6 → M7. M1/M2 are independent and can run in parallel.

## 5. Risks & mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Scope blow-up (three pillars at once) | High | Milestones are independently shippable; UI depends only on stable BFF JSON. |
| Auth regressions lock out services in CI | Med | `local` mode default in dev/CI; `oidc` gated by profile; contract tests for the filter. |
| Postgres adds runtime coupling | Med | In-memory `OutboxRepository`/`DedupeStore`/`MdmStore` remain for unit tests; H2 for repo tests. |
| Outbox change breaks MDM publish semantics | Med | Keep `ReferenceSink` as the port; `MdmPublishIT` asserts end-to-end; versioning unchanged. |
| SPA token handling vulnerabilities | Med | In-memory tokens, PKCE, no `localStorage`; CSP in nginx; short-lived tokens. |
| Keycloak realm drift vs code claims | Low | Realm JSON committed as the source of truth; a smoke test asserts a login token carries `tenant_id`. |

Rollback: services degrade to in-memory stores by removing the datasource; auth can be disabled per env
(`mode=none` is intentionally **not** provided — use `local`). Manifests pin image tags per 0.2.0 policy.

## 6. Release process checklist (M-cut)

1. `main` frozen; CI green (contracts + build + web + smoke).
2. Persistence migrations reviewed; MDM restart test recorded.
3. Version → `0.3.0`; k8s/overlay image tags → `0.3.0`; realm JSON finalized.
4. Image workflow publishes services + `web`; digests captured.
5. Tag `v0.3.0`; GitHub release from `docs/releases/0.3.0.md`.
6. Sign-off: docs + CI + platform + security owners.

## 7. Backlog hand-off → 0.4.0 (candidate)

- L5 orchestration core (saga/statechart), trigger/exception system, decision-context routing.
- SSE/WebSocket live push; `bff-customer`/`bff-partner` + their SPAs.
- ATP/promise engine; intelligence v0; analytics T0–T1.
- Secrets vault, mTLS mesh, multi-region DR drill; projection persistence/rebuild tooling.
