# Local development stack (Phase-1)

Kafka (KRaft, single node) + a one-shot **topic bootstrap** + Kafka UI, plus (0.3.0) **PostgreSQL 16**,
**Keycloak**, and the **web** console. The logistics services run on your machine via
`gradle :<module>:bootRun` and connect to `localhost:9092`.

## Start

```
docker compose -f ops/local-compose/docker-compose.yml up -d
docker compose -f ops/local-compose/docker-compose.yml run --rm topic-bootstrap
```

- Kafka broker: `localhost:9092`
- Kafka UI: http://localhost:8090
- PostgreSQL: `localhost:5432` (user/pass `logistics`/`logistics`; database `mdm`)
- Keycloak: http://localhost:8091 (admin/admin; realm `logistics-os`)
- Web console: http://localhost:8092 (usually run via `npm run dev` instead)
- Topics are created **only** by the bootstrap (Kafka auto-create is disabled). Re-running the
  bootstrap is a safe no-op (`--if-not-exists`).

## Run the services (order matters for the first boot)

| # | Service | Command | Port |
|---|---------|---------|------|
| 1 | mdm (seeds golden records on start) | `gradle :core:mdm:bootRun` | 8086 |
| 2 | ingest-gateway | `gradle :core:ingest-gateway:bootRun` | 8081 |
| 3 | acl-tms | `gradle :core:acl-tms:bootRun` | 8083 |
| 4 | acl-wms | `gradle :core:acl-wms:bootRun` | 8084 |
| 5 | read-model-builder | `gradle :core:read-model-builder:bootRun` | 8085 |
| 6 | control-tower | `gradle :core:control-tower:bootRun` | 8087 |
| 7 | bff-ops | `gradle :core:bff-ops:bootRun` | 8088 |
| 8 | legacy-simulator (optional: feeds data) | `gradle :tools:legacy-simulator:bootRun` | 8089 |

### 0.3.0: authentication + persistence

All HTTP services are secured (ADR-0020). In **local** mode fetch a dev token first:

```
TOKEN=$(curl -s -X POST http://localhost:8088/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"ops","password":"ops"}' | python -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
```

Dev users: `admin`/`admin`, `ops`/`ops` (operator), `viewer`/`viewer`.

Run MDM as a PostgreSQL system-of-record by activating the `jdbc` profile:

```
SPRING_PROFILES_ACTIVE=jdbc MDM_DB_URL=jdbc:postgresql://localhost:5432/mdm \
MDM_DB_USER=logistics MDM_DB_PASSWORD=logistics gradle :core:mdm:bootRun
```

The web console talks to Keycloak when `WEB_AUTH_MODE=oidc` and `WEB_OIDC_ISSUER` is set
(`http://localhost:8091/realms/logistics-os`).

End-to-end smoke (local mode):

```
curl -H "Authorization: Bearer $TOKEN" http://localhost:8086/mdm/location
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8085/views/shipments?status=DISPATCHED"
curl -H "Authorization: Bearer $TOKEN" http://localhost:8087/tower/alerts
curl -H "Authorization: Bearer $TOKEN" http://localhost:8088/ops/overview
curl -X POST http://localhost:8088/ops/actions/notify-customer \
     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"shipmentId":"SHP-<id>"}'
```

## Notes

- All services default to `KAFKA_BOOTSTRAP_SERVERS=localhost:9092` (override via env).
- The broker listens on `PLAINTEXT://localhost:9092` for host access and `kafka:9092` inside the
  compose network (used by the bootstrap container).
- Destroy everything:
  `docker compose -f ops/local-compose/docker-compose.yml down -v`