# Integration Architecture (systems & channels)

> Status: Accepted · Related: [integrations.md](integrations.md) · [events.md](events.md) · [architecture.md](architecture.md) · [cross-cutting.md](cross-cutting.md) · [schemas/common-types.md](schemas/common-types.md) · [ADR-0007](adr/0007-outbox-and-idempotency.md) · [ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md) · [ADR-0016](adr/0016-external-integration-contract.md)

Complements the per-layer patterns in [integrations.md](integrations.md) with a per-**system** view. For every external/adjacent system this document fixes five things: **(1) sync vs. async pattern, (2) API contract style, (3) rate limiting, (4) retry/idempotency, (5) system of record (SoR)**.

Systems covered: ERP (SAP/Oracle), OMS, 3PL/4PL carrier APIs (DHL, FedEx, UPS, regional LTL), warehouse automation (conveyors/AGVs via WCS), driver/worker mobile apps, and customer-facing tracking.

---

## 1. Principles

1. **The bus is the hub.** Every system is reached through an **anti-corruption adapter** that translates to/from the canonical model; the adapter publishes/subscribes on Kafka ([ADR-0010](adr/0010-cots-strangler-and-anti-corruption.md)). No system talks to another system's database.
2. **Async by default; sync only when the caller needs the answer now.** Sync is for read-your-write UX, pricing/labeling that block a transaction, and sub-second queries. Everything that can tolerate seconds-to-minutes is async via events ([ADR-0016](adr/0016-external-integration-contract.md)).
3. **Every mutating integration is idempotent.** Outbound calls carry an idempotency key; inbound feeds dedupe on a provider key. No blind retries of non-idempotent calls ([ADR-0007](adr/0007-outbox-and-idempotency.md)).
4. **Ownership is explicit.** Exactly one SoR per fact. When we are not the SoR, we never overwrite the owner; when we are, external systems consume our events ([data-model.md](data-model.md)).
5. **Reliability is tiered by blast radius** (§8), from exactly-once money effects to best-effort telemetry.
6. **Contract style follows the peer, not our preference.** We adapt to each vendor's REST/EDI/webhook/stream; the canonical contract stays clean behind the ACL.

## 2. Integration styles

| Style | Use when | Transport | Failure mode |
|---|---|---|---|
| **Sync request/response** | caller is blocked (quote, label, credit check, UI read-your-write) | REST/JSON or gRPC (internal) | timeout → caller degrades or queues |
| **Async events** | state propagation, choreography, cross-domain | Kafka (`<layer>.<domain>.<type>.vN`) | at-least-once → dedupe |
| **Webhooks (in)** | peer pushes status/POD/invoice | HTTPS callback | verify signature, enqueue, 2xx fast |
| **Webhooks (out)** | we notify customers/partners | HTTPS callback from our event stream | retry w/ backoff → DLQ |
| **Batch file / EDI** | B2B with legacy/trading partners, periodic | SFTP/AS2 (X12 204/214/210/990/997, EDIFACT) | reconcile by control number; idempotent import |
| **Streaming** | device/telemetry, WCS task feed | MQTT/gRPC streaming/OPC-UA | edge buffering + dedupe |
| **CDC** | legacy/COTS with no event API | Debezium | ordered per row; ACL-mapped |

## 3. Cross-cutting mechanics

Every adapter implements these uniformly.

- **Idempotency**
  - Outbound: `Idempotency-Key` header = `eventType:aggregateId:aggregateVersion` (or the domain command id). Persist an **external-mapping** row `(ourAggregateId ↔ vendorObjectId, idempotencyKey)` for reconciliation.
  - Inbound: dedupe on `providerEventId` when present, else `(source, businessKey, eventTime, payloadHash)`.
  - Consumers: dedupe store keyed by `idempotencyKey` with TTL ≥ topic retention ([common-types.md §4](schemas/common-types.md)).
- **Retries** — exponential backoff with **full jitter**, capped attempts and total deadline; retry only on retryable classes (timeout, 429, 5xx, connection reset). Non-idempotent calls are retried **only** with an idempotency key or via query-before-create.
- **Circuit breakers + bulkheads** — one breaker and one thread/connection pool per external dependency so a slow carrier cannot starve others.
- **Rate limiting** — outbound: token bucket per `(vendor, credential, tenant)`; honor `429` + `Retry-After`; adaptive concurrency; batch/paginate where the API allows. Inbound: per API key/IP quota at the gateway. Illustrative defaults in §4 are **to be tuned per contract**, not vendor guarantees.
- **Ordering** — cross-entity ordering is not assumed; per-aggregate ordering preserved via partition key = aggregate id. Late/out-of-order events are handled by version guards (`aggregateVersion`, provider sequence).
- **Time** — reject or flag events outside a clock-skew window; store both source `eventTime` and ingestion `receivedAt` (`timeProvenance` flag).
- **DLQ + replay** — poison payloads to `dlq.<topic>` with `errorReason`; replayable after fix ([events.md §7](events.md)).
- **Security** — OAuth2 client-credentials or mTLS to vendors; HMAC-signed webhooks with replay window; secrets in a vault; all access on `audit.*`; strict tenant scoping.

## 4. Per-system integration design

### 4.1 ERP — SAP / Oracle

**Role:** finance backbone. **We are SoR for** logistics execution (shipments, milestones, inventory movement, carrier cost events). **ERP is SoR for** GL/AP/AR, cost accounting, tax, and the **financial view** of customer/vendor/material master.

| Aspect | Design |
|---|---|
| **Sync vs async** | Master data **sync** (read, cached); financial postings **async** (never block execution on ERP); GL reversal/period-close **batch**. |
| **Contract style** | SAP: OData/BAPI/RFC or IDoc; Oracle: REST/DB or FBDI/interface tables. Behind a dedicated `erp-adapter` (ACL). Batch IDoc/FBDI via SFTP for volume postings. |
| **Rate limiting** | ERP is a shared scarce resource: strict per-credential token bucket, off-peak windows for batch, request coalescing, no chatty per-event posting (aggregate into documents). |
| **Retry/idempotency** | **Ledger posting must be exactly-once by effect.** Our `settlementId`/`invoiceId` is the idempotency key; ERP returns/echoes the document number → stored in the external-mapping table. Retries are safe; duplicate posting is prevented by the business key. Master-data sync is read-only (idempotent). |
| **SoR** | **ERP**: GL, AP/AR, tax, cost centers. **Us**: shipment/inventory/carrier-cost events that feed those postings. |

Noise: never let an ERP outage halt picking or delivery; financial events queue durably and post when ERP recovers (Tier 0 reconciliation).

### 4.2 OMS

Two cases: **our OMS** (L3 execution service, SoR for the order) and an **external/legacy or channel OMS** (e-commerce/B2B front end).

| Aspect | Design |
|---|---|
| **Sync vs async** | Order capture **sync** (validate + read-your-write confirmation); status/fulfillment updates **async** (`ord.order.events.v1`). Cross-system order flows are **sagas**, not distributed transactions ([ADR-0004](adr/0004-saga-over-distributed-tx.md)). |
| **Contract style** | Our OMS: REST/GraphQL for capture + BFF; `ord.order.events.v1` for propagation. External/channel OMS: vendor REST + webhooks, or EDI 850/855 (PO/ack); wrapped by an ACL. |
| **Rate limiting** | Inbound capture via API gateway per API key; burst-friendly for flash sales; idempotent submit endpoint. |
| **Retry/idempotency** | `orderId`/client `submitToken` is the idempotency key; duplicate submits return the existing order. Order-modifying commands carry `commandId`. |
| **SoR** | **Us** for the canonical order lifecycle once accepted; the originating channel is SoR for the *raw* submission/quote until accepted. Inventory availability/ATP is **Orchestration**-owned. |

### 4.3 Carrier APIs — DHL, FedEx, UPS, regional LTL

**Role:** execution of transport. **Carriers are SoR for** their network's raw scan events, the assigned tracking/pro number, and label identity. **We are SoR for** the tender, the canonical shipment, the normalized milestone projection, and freight cost.

| Aspect | Design |
|---|---|
| **Sync vs async** | Rating/labeling/tender **sync** (blocking a shipment); pickup scheduling sync; **status and invoice async** via webhook/EDI. |
| **Contract style** | Parcel (DHL/FedEx/UPS): **REST + OAuth2**, JSON, ZPL/PDF labels; webhooks for status where offered. LTL/regional: **EDI X12** — 204 (load tender), 990 (response), 214 (status), 210 (invoice), 997 (ack), plus 211/NMFC freight detail; some offer REST. Multimodal/ocean: booking/EDIFACT. All behind a per-carrier `carrier-adapter`. |
| **Rate limiting** | Per-carrier token bucket (parcel APIs cap requests/min and have move/rate quotas); batch address validation; cache rate quotes briefly; bulkhead per carrier. On `429`, back off with jitter and shed non-critical polling in favor of webhooks. |
| **Retry/idempotency** | `TenderShipment` is idempotent by `tenderId`/shipment id where the carrier supports a client reference; label creation retried with the same key to avoid duplicate labels/charges. If a vendor lacks an idempotency key, use **query-before-create** (look up by our reference) before retrying. Inbound status/invoice dedupe on carrier event id / invoice number. |
| **SoR** | **Carrier**: raw tracking scans, pro/tracking number, carrier invoice. **Us**: tender decision, canonical `Shipment`, normalized `Milestone`, freight accrual. |

Special notes: **never auto-retry a tender without an idempotency key** (duplicate pickup risk); **validate labels/rates** before commit; isolate carriers with circuits so one vendor's outage degrades only that lane.

### 4.4 Warehouse automation — conveyors / AGVs via WCS

**Role:** physical execution inside the four walls. **WMS is SoR for** inventory and pick/pack tasks. **WCS is SoR for** equipment/device state and physical conveyance execution.

| Aspect | Design |
|---|---|
| **Sync vs async** | Task dispatch/feedback **sync, low-latency** (handshake must complete a move); telemetry/health **async stream**. WMS↔WCS uses a tight request/ack handshake with timeouts. |
| **Contract style** | Internal: **gRPC (bidirectional streaming)** between WMS and WCS; device layer: **OPC-UA / MQTT** to PLCs, AGV fleet managers via their own API. Canonical events (`wms.task.events.v1`, `inv.inventory.events.v1`, `obs.scan.v1`) published by the WMS/WCS adapter. |
| **Rate limiting** | High throughput, not internet rate limits: back-pressure via bounded queues and flow control; admission control at the WMS; never let the bus throttle a physical line — buffer at the edge. |
| **Retry/idempotency** | `taskId`/`moveId` is the idempotency key. **Physical side effects are not blindly retried**: on timeout/uncertainty, **reconcile by scan** (next barcode read confirms/denies the move) rather than re-issuing the command. Scans are the ground truth for inventory. |
| **SoR** | **WMS**: inventory quantity/location and task state. **WCS**: equipment/device state. Neither is SoR for the order or shipment. |

Special notes: deterministic idempotent task commands; explicit **infeasible/blocked** responses (never silent drop); safety interlocks are WCS-owned and never overridden by orchestration.

### 4.5 Driver / worker mobile apps

**Role:** field capture and execution UX. The app is the **origin of capture** for scans, signatures, photos, and status at the moment of action; the platform becomes SoR of the canonical record once synced.

| Aspect | Design |
|---|---|
| **Sync vs async** | **Offline-first.** Capture is local; sync is **async with a local durable queue**; push notifications (FCM/APNs) and WebSocket for live updates. Sync is **delta-based** and resumable. |
| **Contract style** | REST/GraphQL over HTTPS for sync (+ optional gRPC for bulk), push for wake-ups, WebSocket/SSE for live; media uploaded separately (pre-signed object storage), referenced by id. |
| **Rate limiting** | Client-side batching/throttling and exponential backoff; server quotas per user/device; sync priority lanes (POD and scans ahead of analytics). |
| **Retry/idempotency** | Every action carries a **client-generated UUID**; server dedupes. Conflict resolution is **server-side**: per-field **last-writer-wins** with domain rules (e.g., a POD cannot be overwritten once accepted; corrections are new events). Batch sync is idempotent per action id. |
| **SoR** | **App** is the capture origin (signature/photo/scan bytes); **Us** are SoR for the canonical `Milestone`, POD record, and task/inventory effects once synced. |

Special notes: POD is **evidence** — store signature image hash + timestamp + geolocation; immutability after acceptance (corrections are append-only). Handle clock skew by trusting server time on sync.

### 4.6 Customer-facing tracking

**Role:** experience/visibility to the customer. **We are SoR for** tracking/visibility (milestones, ETA, exceptions). Customers only consume.

| Aspect | Design |
|---|---|
| **Sync vs async** | **Read sync** (portal/API pull); **push async** (webhooks/WebSocket/SSE, email/SMS notifications). No customer write path into domain state. |
| **Contract style** | Public **REST tracking API** (per-tenant, per-key) + **outbound webhooks** (HMAC-signed, versioned payloads) + **WebSocket/SSE** for live; **EDI 214** for B2B retail/partners; white-label portal via BFF. |
| **Rate limiting** | Per API key/IP quota at the gateway; higher tiers per contract; webhook fan-out with per-destination concurrency limits; cache public tracking responses briefly. |
| **Retry/idempotency** | Outbound webhook delivery retried with backoff + jitter, `eventId` for consumer dedupe, per-destination DLQ; subscribe/unsubscribe is idempotent. Inbound (rare) is read-only. |
| **SoR** | **Us** (Visibility). Customers never own milestone/canonical state. |

Special notes: privacy — change tracker keys expire at POD (PII minimization); webhook payloads carry only what the subscriber is entitled to see; tenant isolation enforced on every read.

## 5. Per-system summary matrix

| System | Direction | Sync/Async | Contract style | Rate limiting | Retry & idempotency | System of record |
|---|---|---|---|---|---|---|
| **ERP (SAP/Oracle)** | bi-dir | master-data sync; postings async; period-close batch | OData/BAPI/RFC/IDoc; Oracle REST/FBDI; SFTP | scarce shared resource; token bucket, off-peak batch, coalesced documents | exactly-once by business key (`settlementId`/`invoiceId`); doc-number echo in mapping table | **ERP**: GL/AP/AR, tax, cost. **Us**: exec events feeding postings |
| **OMS** (ours) | in/out | capture sync; propagation async | REST/GraphQL + `ord.order.events.v1` | per API key at gateway; burst-friendly | `orderId`/`submitToken` dedupe; commands carry `commandId` | **Us** for canonical order; channel owns raw submission |
| **OMS** (external/legacy) | bi-dir | capture sync; status async | vendor REST + webhooks / EDI 850/855 | vendor limits + gateway quota | client ref key; query-before-create | vendor until accepted → then us |
| **Carrier APIs** | bi-dir | rate/label/tender sync; status/invoice async | REST+OAuth2 (parcel); X12 204/990/214/210 (LTL) | per-carrier token bucket; webhooks over polling; bulkheads | `tenderId`/label key; no unkeyed tender retries; query-before-create | **Carrier**: raw scans, pro#, invoice. **Us**: tender, shipment, milestone, accrual |
| **Warehouse automation (WCS)** | bi-dir | task handshake sync; telemetry async | gRPC streaming (WMS↔WCS); OPC-UA/MQTT devices | back-pressure/flow control, bounded queues | `taskId`/`moveId`; reconcile by scan, never blind re-issue | **WMS**: inventory/tasks. **WCS**: equipment state |
| **Driver/worker mobile** | bi-dir | offline-first; async delta sync | REST/GraphQL + push (FCM/APNs) + WS/SSE | client batching/backoff; per-user quota | client UUID per action; server dedupe; LWW + append-only corrections | **App**: capture origin; **Us**: canonical milestone/POD |
| **Customer tracking** | out/read | read sync; push async | REST tracking API + HMAC webhooks + WS/SSE + EDI 214 | per-key/IP at gateway; per-destination webhook concurrency | `eventId` consumer dedupe; webhook retry→DLQ; idempotent subscribe | **Us** (Visibility) |

## 6. Data ownership (system of record)

| Fact / object | System of record | Everyone else reads via |
|---|---|---|
| GL entry, AP/AR, tax, cost accounting | **ERP** | ERP reports; `fin.*` events mirrored |
| Customer/vendor/material **financial** master | **ERP** | ERP master sync → MDM golden record |
| Canonical `Order` lifecycle | **OMS (ours)** | `ord.order.events.v1`, `order_view` |
| ATP / `Promise` | **Orchestration** | `orch.promise.v1` |
| Canonical `Shipment`, tender decision, route | **TMS / Orchestration** | `tms.*`, `shipment_view` |
| Carrier raw scan, pro/tracking number | **Carrier** | carrier webhooks/EDI → normalized `Milestone` |
| Canonical `Milestone`, ETA, exception | **Visibility / Intelligence** | `vis.*`, `intel.prediction.v1` |
| Physical inventory qty/location, task state | **WMS** | `inv.*`, `inventory_view` |
| Equipment/device state, conveyance execution | **WCS** | WCS telemetry → `obs.*`/`wms.*` |
| POD (signature/photo evidence) | **Us** (capture origin: mobile app) | `vis.*`, portal |
| Freight invoice / settlement | **Carrier invoice** → **Billing (ours)** after audit | `fin.bill`, `fin.settlement.events.v1` |
| Charge / Invoice to customer | **Billing (ours)** | `fin.*`; posted to ERP |
| Customer-tracker status | **Us** (Visibility) | tracking API, webhooks, WS/SSE |

## 7. Lifecycle sequence: order → POD → settlement

All touchpoints, mixing sync (blocking) and async (event) steps. Diagram: [`diagrams/order-pod-settlement.mmd`](diagrams/order-pod-settlement.mmd).

1. **Order** — channel submits to OMS (sync). OMS does a **sync ERP** credit check + customer master lookup; on OK emits `ord.order.created.v1` (async).
2. **Promise** — Orchestration reserves ATP and requests a **sync carrier rate** for the quote; commits `orch.promise.v1`; confirms to the customer (sync/webhook).
3. **ERP** — sales-order/cost accrual posted **async** (idempotent by document key).
4. **Fulfill** — Orchestration issues `Allocate/Pick` to WMS; WMS drives **WCS** (conveyor/AGV) via **sync gRPC handshake**; scans reconcile inventory; `wms.task.events.v1`, `inv.inventory.events.v1`.
5. **Tender/ship** — Orchestration → TMS → **carrier sync API/EDI** create shipment + label (idempotent by `tenderId`); carrier returns tracking/label; `tms.tender.events.v1`, `tms.shipment.events.v1`.
6. **Dispatch/pickup** — manifest pushed to **driver mobile** (push); driver captures pickup scans offline-capable; syncs (client UUID, idempotent).
7. **In transit** — carrier **status webhooks / EDI 214** → Visibility normalizes → `vis.milestone.events.v1`; telemetry streams.
8. **POD** — driver captures signature/photo/scan in app → delta sync → Visibility `delivered` milestone; `vis.*`; **customer webhook/SSE/214** pushed (retry → DLQ).
9. **Settlement** — on `shipment.delivered`, Billing **accrues**; carrier invoice (EDI 210/webhook) → **freight audit** → match; **ERP GL post** (async, exactly-once by `settlementId`); customer invoice → ERP AR; payment settles; `fin.settlement.events.v1`.

## 8. Reliability tiers & SLAs

| Tier | Blast radius | Examples | Delivery | Idempotency | Recovery |
|---|---|---|---|---|---|
| **0 Money/inventory** | financial / stock integrity | ERP postings, settlement, inventory moves | must not lose; exactly-once **effects** | business key mandatory | reconcile + ledger insert-or-ignore |
| **1 Shipment execution** | a shipment/lane | tender, label, task dispatch | at-least-once, per-aggregate ordered | `tenderId`/`taskId` | retry keyed; query-before-create |
| **2 Status/telemetry** | observability/UX | carrier scans, telemetry, milestones | at-least-once, loss tolerable, dedupe | provider event id | lazy reconcile |
| **3 Analytics firehose** | reporting only | raw feeds, device noise | best-effort, sampled | n/a | backfill from lake |

Target latency: sync calls p99 < 1 s (rating/label/UI), p99 < 100 ms for WCS handshake; async propagation p99 < 2 s within platform, minutes tolerated to external partners.

## 9. Governance checklist (per integration)

- [ ] Named SoR for every fact; write authority documented.
- [ ] ACL adapter with schema validation, DLQ, and external-mapping table.
- [ ] Idempotency key defined for every mutating call; dedupe for every inbound feed.
- [ ] Retry policy (classes, backoff+jitter, deadline) and circuit breaker/bulkhead.
- [ ] Rate-limit posture (token bucket, batch/off-peak, `429`/`Retry-After`).
- [ ] Security: OAuth2/mTLS, HMAC webhooks, secret storage, tenant scoping, audit.
- [ ] Contract tests + versioning per [ADR-0008](adr/0008-schema-registry-and-versioning.md).
- [ ] Runbook: reconciliation, DLQ triage, failover/degraded mode.
