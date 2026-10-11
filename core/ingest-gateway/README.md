# ingest-gateway (core, L1)

Terminates inbound observation streams, validates them against the canonical obs payload schemas,
dedupes, and publishes canonical envelopes.

## Endpoint

`POST /ingest/{kind}` where kind is `telemetry`, `scan` or `carrier-status`

Request headers:

- `X-Tenant-Id` (required)
- `X-Source` (optional) — producer of the feed, e.g. `truck-sim`
- `X-Idempotency-Key` (optional) — client-retry key; otherwise derived from source + body hash

Body: raw JSON, validated against the kind's payload schema via `PayloadAvro.jsonToGeneric`.

Response `200`, `{ "status": "ACCEPTED" | "DUPLICATE", "eventId": "...", "message": null }`
or `400` `{ "status": "REJECTED", "message": "..." }`.

## Flow

1. resolve payload schema (`<layer>.<domain>.v1` subject convention, classpath resources locally)
2. validate JSON body
3. claim idempotency key (`GuardedProcessor`, in-memory dedupe store; TTL 48h)
4. build `EventEnvelope` (aggregate key = deviceId / locationId / shipmentId)
5. publish to `obs.telemetry.v1` / `obs.scan.v1` / `obs.carrier-status.v1`, keyed by aggregate id

## Local/CI

- `schema.resource=true`: schemas are copied from `contracts/` into resources by the build.
- Requires a Kafka broker (`spring.kafka.bootstrap-servers` or `KAFKA_BOOTSTRAP_SERVERS`).
- Tests: `IngestServiceTest` (pure unit) + `KafkaIngestPublisherIT` (embedded Kafka roundtrip).