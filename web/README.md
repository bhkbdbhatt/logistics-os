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

## Run locally

Prerequisites: Node.js 18+ and npm. The API calls are proxied to `bff-ops`, so start that
service on `http://localhost:8088` first.

```sh
npm install                       # install dependencies
npm run dev                       # start Vite dev server
```

Then open http://localhost:5173. The dev server proxies `/ops`, `/tower`, `/auth` and `/mdm`
to `VITE_DEV_PROXY` (default `http://localhost:8088`).

To point the proxy at a backend running elsewhere, create `web/.env.local` (gitignored; see
`.env.example`) with the base URL — this is required if `bff-ops` isn't on `localhost:8088`:

```sh
VITE_DEV_PROXY=http://my-backend:8088
```

Login failing with a `500` on `POST /auth/login` means the proxy can't reach the backend — check
that the service is up at `VITE_DEV_PROXY`.

Auth defaults to `local` mode via `public/config.js` (`window.__LOGISTICS_CONFIG__`). To use
OIDC instead, edit that file and set `mode: 'oidc'` with an `oidc` block (issuer, clientId,
redirectUri).

## Commands

```sh
npm install
npm run dev        # http://localhost:5173, proxying /ops,/auth to http://localhost:8088
npm run build      # typecheck + production build to dist/
npm run preview    # serve the production build at http://localhost:5173
npm run typecheck  # tsc --noEmit
npm run lint       # tsc --noEmit
```

## Container

Built from `web/Dockerfile` (static site on nginx, reverse-proxying `/ops` and `/auth` to `bff-ops`).
Runtime env: `LOGISTICS_WEB_API_BASE_URL`, `LOGISTICS_WEB_AUTH_MODE`, `LOGISTICS_WEB_OIDC_ISSUER`,
`LOGISTICS_WEB_OIDC_CLIENT_ID`.
