# platform:security

OAuth2 resource-server wiring plus a local-JSON dev mode, and the request-scoped tenant binding
services rely on. Spring auto-configured; drop it on a service classpath and set
`logistics.security.*`. See [ADR-0020](../../docs/adr/0020-identity-authentication-authorization.md).

## Modes

| `logistics.security.mode` | Behavior |
|---------------------------|----------|
| `local` (default) | Issue + validate **HS256** tokens from `users.json`; `POST /auth/login` open. For dev/CI/offline. Not for production. |
| `oidc` | Validate JWTs from `logistics.security.issuer-uri` (Keycloak/Entra/Okta); no login endpoint; browser uses Authorization Code + PKCE. |

## What it configures

- A stateless `SecurityFilterChain`: `/actuator/health/**` + `/actuator/info` open, OPTIONS open,
  `/auth/login` open in `local` mode, **everything else authenticated**.
- A `JwtAuthenticationConverter`: scopes → `SCOPE_*`, the `roles` claim → `ROLE_<ROLE>` (enables
  `@PreAuthorize("hasRole('OPERATOR')")`).
- `TenantContextFilter`: binds `TenantContext` from the `tenant_id` claim for the request and rewrites
  the `X-Tenant-Id` header so downstream code never trusts the client value. A token **without** a
  tenant claim is rejected with 403.

## Properties

```yaml
logistics:
  security:
    mode: oidc                     # local | oidc
    issuer-uri: https://keycloak/realms/logistics-os
    audience: logistics-os-api     # optional
    tenant-claim: tenant_id        # dotted path
    roles-claim: roles             # dotted path (Keycloak: realm_access.roles)
    username-claim: preferred_username
    local:
      secret: ${SECURITY_LOCAL_SECRET}   # >= 32 bytes for HS256
      users-file: /etc/logistics-os/users.json
      token-ttl: 1h
```

`users.json`:

```json
{ "users": [
  { "username": "admin", "password": "{noop}admin", "tenant": "acme",
    "roles": ["admin", "operator", "viewer"] }
] }
```

Passwords use Spring's delegating `PasswordEncoder` (`{noop}` for dev, `{bcrypt}` for real). With no
`users-file`, three built-in dev users are used (`admin`/`ops`/`viewer`, tenant `acme`). Passing
`secret` shorter than 32 bytes, or `mode=oidc` without `issuer-uri`, fails fast at startup.

## Keycloak

Realm roles must be exposed as a top-level `roles` claim (add a "User Realm Role" mapper) and the
tenant as a `tenant_id` user-attribute claim — see [ops/local-compose](../../ops/local-compose/README.md).

## Testing

`ApplicationContextRunner` covers bean wiring in `local` mode; `LocalTokenServiceTest` and
`TenantContextFilterTest` exercise real Nimbus HS256 tokens end-to-end without an IdP.
