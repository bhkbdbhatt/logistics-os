# Logistics OS - Ops Console (web)

React + TypeScript + Vite single-page app that fronts the platform through the ops BFF
(`bff-ops`). It never talks to L3/L5 services directly (ADR-0022).

## Surfaces

- **Dashboard** - shipment/alert counts, upstream source health, edition badge (`/ops/overview`, `/ops/entitlement`).
- **Shipments** - shipment list from the overview; detail view (`/ops/shipments/{id}`) with the operator-gated
  "Notify customer" action (`/ops/actions/notify-customer`).
- **Alerts** - open control-tower alerts composed into the overview.

## Auth

- `local` (default): posts credentials to `POST /auth/login` and keeps the returned HS256 token in
  `sessionStorage`.
- `oidc`: Authorization Code + PKCE against the configured Keycloak issuer (dependency-free; uses Web Crypto).

The runtime config is injected into `window.__LOGISTICS_CONFIG__` via `public/config.js`
(dev) or the container entrypoint (`LOGISTICS_WEB_*` env vars).

## Commands

```sh
npm install
npm run dev        # http://localhost:5173, proxying /ops,/auth to http://localhost:8088
npm run build      # typecheck + production build to dist/
npm run typecheck  # tsc --noEmit
npm run lint       # tsc --noEmit
```

## Container

Built from `web/Dockerfile` (static site on nginx, reverse-proxying `/ops` and `/auth` to `bff-ops`).
Runtime env: `LOGISTICS_WEB_API_BASE_URL`, `LOGISTICS_WEB_AUTH_MODE`, `LOGISTICS_WEB_OIDC_ISSUER`,
`LOGISTICS_WEB_OIDC_CLIENT_ID`.
