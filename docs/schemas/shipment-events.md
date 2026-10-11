# Shipment / Trip Event Schemas

> Status: Accepted · Related: [common-types.md](common-types.md) · [reference-data.md](reference-data.md) · [../events.md](../events.md) · [../data-model.md](../data-model.md)

Core aggregate: **Shipment** (a.k.a. Trip/Consignment). A shipment follows a lifecycle from order placement → pickup → line-haul → last-mile → POD → settlement. Each transition emits exactly one canonical event on topic **`tms.shipment.events.v1`** (subject `tms.shipment.events.v1-value`), UUID-keyed by `shipmentId` for per-aggregate ordering.

All payloads below are the `payload` bytes inside the [EventEnvelope](common-types.md#2-event-envelope). The envelope supplies `tenantId`, `aggregateVersion`, `idempotencyKey`, `occurredAt`, etc.

## 1. Lifecycle state machine

```
                       ┌─────────────┐
                       │  (created)  │  ← ShipmentCreated (order placed / booked)
                       └──────┬──────┘
                              ▼
                       ┌─────────────┐
                       │   PLANNED   │  ← ShipmentPlanned
                       └──────┬──────┘
                              ▼
                       ┌─────────────┐   ShipmentTenderDeclined
                       │  TENDERED   │◀───────────────┐
                       └──────┬──────┘                 │
                              ▼ ShipmentTenderAccepted │
                       ┌─────────────┐                 │
                       │  DISPATCHED │                 │
                       └──────┬──────┘                 │
                              ▼                         │
                       ┌─────────────┐                 │
                       │  PICKED_UP  │                 │
                       └──────┬──────┘                 │
                              ▼                         │
                       ┌─────────────┐  Arrived/Departed│
                       │ IN_TRANSIT  │◀──(line-haul legs)
                       └──────┬──────┘
                              ▼
                       ┌──────────────────┐
                       │ OUT_FOR_DELIVERY │  (last-mile)
                       └────────┬─────────┘
                                ▼ ShipmentDelivered + POD
                       ┌─────────────┐
                       │  DELIVERED  │
                       └──────┬──────┘
                              ▼ ShipmentSettled
                       ┌─────────────┐
                       │  SETTLED    │ (terminal)
                       └─────────────┘

  Cross-cutting / terminal:
     ShipmentExceptionRaised / ShipmentExceptionResolved  (any state)
     ShipmentCancelled                                    (pre-delivery)
     ShipmentReturned                                     (post-delivery)
     ShipmentPodDocumentAttached                          (post-delivery)
```

**Status enum**

```json
{
  "type": "enum",
  "name": "ShipmentStatus",
  "namespace": "io.logisticsos.shipment",
  "symbols": ["CREATED","PLANNED","TENDERED","DISPATCHED","PICKED_UP",
              "IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","SETTLED",
              "EXCEPTION","CANCELLED","RETURNED"]
}
```

## 2. Event index

Topic for all: **`tms.shipment.events.v1`**. Key = `shipmentId`.

| # | Event | From → To | Emitter(s) | Primary consumers |
|---|---|---|---|---|
| 1 | `ShipmentCreated` | (new) → CREATED | OMS (or TMS on order capture) | Orchestrator, TMS, Visibility, Billing (accrual), Analytics |
| 2 | `ShipmentPlanned` | CREATED → PLANNED | TMS (planner/orchestrator) | Orchestrator, Visibility, YMS (appointments), Analytics |
| 3 | `ShipmentTendered` | PLANNED → TENDERED | TMS | Orchestrator, Billing, Analytics |
| 4 | `ShipmentTenderAccepted` | TENDERED → DISPATCHED(pre) | TMS, Carrier (via EDI/API ACL) | Orchestrator, Visibility, YMS, Billing |
| 5 | `ShipmentTenderDeclined` | TENDERED → TENDERED | TMS, Carrier (via ACL) | Orchestrator (re-tender), Analytics |
| 6 | `ShipmentDispatched` | → DISPATCHED | TMS / Driver app | Visibility, YMS, Orchestrator, Analytics |
| 7 | `ShipmentPickedUp` | DISPATCHED → PICKED_UP | TMS / Driver app / WMS scanner | Visibility, Orchestrator, Billing, Analytics |
| 8 | `ShipmentArrivedAtFacility` | → IN_TRANSIT | TMS / Visibility (geofence) | Visibility, YMS, Orchestrator, L6, Analytics |
| 9 | `ShipmentDepartedFacility` | IN_TRANSIT → IN_TRANSIT | TMS / Visibility | Visibility, Orchestrator, Analytics |
| 10 | `ShipmentOutForDelivery` | IN_TRANSIT → OUT_FOR_DELIVERY | TMS / Driver app | Visibility, L6 (customer), Intelligence (ETA), Analytics |
| 11 | `ShipmentDelivered` | OUT_FOR_DELIVERY → DELIVERED | TMS / Driver app | Visibility, Orchestrator, Billing, OMS, L6, Analytics |
| 12 | `ShipmentPodDocumentAttached` | DELIVERED → DELIVERED | TMS / capture service | Visibility, Billing, Analytics |
| 13 | `ShipmentExceptionRaised` | any → EXCEPTION | TMS, Intelligence (anomaly), Orchestrator | Orchestrator, Control Tower (L6), Intelligence, Analytics |
| 14 | `ShipmentExceptionResolved` | EXCEPTION → prior | Orchestrator, TMS | L6, Billing, Analytics |
| 15 | `ShipmentCancelled` | pre-delivery → CANCELLED | OMS / Orchestrator | TMS, WMS, Billing, Visibility, Analytics |
| 16 | `ShipmentReturned` | DELIVERED → RETURNED | TMS / OMS | OMS, WMS, Billing, Analytics |
| 17 | `ShipmentSettled` | DELIVERED → SETTLED | Billing | Orchestrator, OMS, Analytics (finance) |

**Common required fields in every payload:** `shipmentId`, `status` (new status), `previousStatus` (optional for created), plus the event-specific fields below. All timestamps are epoch-millis UTC.

## 3. Event payload schemas

### 3.1 `ShipmentCreated` — order placement

| Field | Req/Opt | Type | Notes |
|---|---|---|---|
| shipmentId | Req | string | ULID |
| externalRefs | Req | array<ExternalRef> | order/customer refs (≥1) |
| shipper | Req | PartyRef | |
| consignee | Req | PartyRef | |
| pickupLocation | Req | LocationRef | |
| deliveryLocation | Req | LocationRef | |
| pickupWindow | Req | TimeWindow | |
| deliveryWindow | Req | TimeWindow | |
| serviceLevel | Req | string | e.g. GROUND_2DAY, SAME_DAY |
| mode | Req | enum Mode | |
| totalQuantity | Req | Quantity | |
| totalWeightKg | Req | double | |
| status | Req | ShipmentStatus | CREATED |
| declaredValue | Opt | Money | |
| commodities | Opt | array<Commodity> | |
| specialInstructions | Opt | string | |
| temperatureRange | Opt | TemperatureRange | |
| hazmat | Opt | HazmatInfo | |
| requestedCarrier | Opt | CarrierRef | |
| insurance | Opt | Money | |
| createdBy | Opt | string | actor id |

```json
{
  "type": "record",
  "name": "ShipmentCreated",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "externalRefs", "type": { "type": "array", "items": {
        "type": "record", "name": "ExternalRef", "fields": [
          { "name": "type", "type": "string", "doc": "ORDER|PO|PRO|CUSTOMER|TRACKING" },
          { "name": "value", "type": "string" }
        ] } } },
    { "name": "shipper",        "type": "io.logisticsos.types.PartyRef" },
    { "name": "consignee",      "type": "io.logisticsos.types.PartyRef" },
    { "name": "pickupLocation", "type": "io.logisticsos.types.LocationRef" },
    { "name": "deliveryLocation","type": "io.logisticsos.types.LocationRef" },
    { "name": "pickupWindow",   "type": "io.logisticsos.types.TimeWindow" },
    { "name": "deliveryWindow", "type": "io.logisticsos.types.TimeWindow" },
    { "name": "serviceLevel",   "type": "string" },
    { "name": "mode",           "type": { "type": "enum", "name": "Mode",
        "symbols": ["FTL","LTL","PARCEL","LAST_MILE","RAIL","OCEAN","AIR","MULTIMODAL"] } },
    { "name": "totalQuantity",  "type": "io.logisticsos.types.Quantity" },
    { "name": "totalWeightKg",  "type": "double" },
    { "name": "status",         "type": "ShipmentStatus" },
    { "name": ["null","io.logisticsos.types.Money"], "name": "declaredValue", "default": null },
    { "name": ["null", {"type":"array","items": {
        "type":"record","name":"Commodity","fields":[
          {"name":"sku","type":"string"},
          {"name":"description","type":"string"},
          {"name":"quantity","type":"io.logisticsos.types.Quantity"},
          {"name":["null","string"],"name":"hsCode","default":null},
          {"name":["null","io.logisticsos.types.Money"],"name":"unitValue","default":null}
        ]}}], "name": "commodities", "default": null },
    { "name": ["null","string"], "name": "specialInstructions", "default": null },
    { "name": ["null", {"type":"record","name":"TemperatureRange","fields":[
        {"name":"minC","type":"double"},{"name":"maxC","type":"double"}]}],
      "name": "temperatureRange", "default": null },
    { "name": ["null", {"type":"record","name":"HazmatInfo","fields":[
        {"name":"unNumber","type":"string"},{"name":"class","type":"string"},
        {"name":"packingGroup","type":"string"}]}], "name": "hazmat", "default": null },
    { "name": ["null","io.logisticsos.types.CarrierRef"], "name": "requestedCarrier", "default": null },
    { "name": ["null","io.logisticsos.types.Money"], "name": "insurance", "default": null },
    { "name": ["null","string"], "name": "createdBy", "default": null }
  ]
}
```

### 3.2 `ShipmentPlanned`

| Field | Req/Opt | Type | Notes |
|---|---|---|---|
| shipmentId | Req | string | |
| planId | Req | string | |
| mode | Req | Mode | may refine created |
| plannedPickup | Req | timestamp | |
| plannedDelivery | Req | timestamp | |
| stops | Req | array<PlannedStop> | route legs |
| status | Req | ShipmentStatus | PLANNED |
| carrier | Opt | CarrierRef | selected |
| loadId | Opt | string | consolidation |
| equipmentType | Opt | string | |
| estimatedDistanceKm | Opt | double | |
| estimatedDurationMin | Opt | int | |
| co2EstimateGrams | Opt | long | |
| optimizationScore | Opt | double | |

```json
{
  "type": "record",
  "name": "ShipmentPlanned",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "planId", "type": "string" },
    { "name": "mode", "type": "Mode" },
    { "name": "plannedPickup", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "plannedDelivery", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "stops", "type": { "type":"array","items":{
        "type":"record","name":"PlannedStop","fields":[
          {"name":"sequence","type":"int"},
          {"name":"location","type":"io.logisticsos.types.LocationRef"},
          {"name":"type","type":{"type":"enum","name":"StopType",
            "symbols":["PICKUP","DELIVERY","HUB","CROSS_DOCK"]}},
          {"name":"window","type":"io.logisticsos.types.TimeWindow"},
          {"name":"legMode","type":"Mode"}
        ]}}},
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null","io.logisticsos.types.CarrierRef"], "name": "carrier", "default": null },
    { "name": ["null","string"], "name": "loadId", "default": null },
    { "name": ["null","string"], "name": "equipmentType", "default": null },
    { "name": ["null","double"], "name": "estimatedDistanceKm", "default": null },
    { "name": ["null","int"], "name": "estimatedDurationMin", "default": null },
    { "name": ["null","long"], "name": "co2EstimateGrams", "default": null },
    { "name": ["null","double"], "name": "optimizationScore", "default": null }
  ]
}
```

### 3.3 `ShipmentTendered`

| Field | Req/Opt | Type | Notes |
|---|---|---|---|
| shipmentId | Req | string | |
| tenderId | Req | string | |
| carrier | Req | CarrierRef | target |
| offeredRate | Req | Money | |
| tenderSequence | Req | int | retries |
| expiresAt | Req | timestamp | |
| tenderMethod | Req | enum | EDI_204 / API / EMAIL |
| status | Req | ShipmentStatus | TENDERED |
| alternateCarriers | Opt | array<CarrierRef> | |
| notes | Opt | string | |

```json
{
  "type": "record",
  "name": "ShipmentTendered",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "tenderId", "type": "string" },
    { "name": "carrier", "type": "io.logisticsos.types.CarrierRef" },
    { "name": "offeredRate", "type": "io.logisticsos.types.Money" },
    { "name": "tenderSequence", "type": "int" },
    { "name": "expiresAt", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "tenderMethod", "type": {"type":"enum","name":"TenderMethod",
        "symbols":["EDI_204","API","EMAIL","PORTAL"]} },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null",{"type":"array","items":"io.logisticsos.types.CarrierRef"}],
      "name": "alternateCarriers", "default": null },
    { "name": ["null","string"], "name": "notes", "default": null }
  ]
}
```

### 3.4 `ShipmentTenderAccepted`

| Field | Req/Opt | Type | Notes |
|---|---|---|---|
| shipmentId | Req | string | |
| tenderId | Req | string | |
| carrier | Req | CarrierRef | |
| acceptedRate | Req | Money | |
| acceptedAt | Req | timestamp | |
| status | Req | ShipmentStatus | |
| vehicle | Opt | VehicleRef | |
| driver | Opt | DriverRef | |
| driverPhone | Opt | string | |
| etaPickup | Opt | timestamp | |

```json
{
  "type": "record",
  "name": "ShipmentTenderAccepted",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "tenderId", "type": "string" },
    { "name": "carrier", "type": "io.logisticsos.types.CarrierRef" },
    { "name": "acceptedRate", "type": "io.logisticsos.types.Money" },
    { "name": "acceptedAt", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null","io.logisticsos.types.VehicleRef"], "name": "vehicle", "default": null },
    { "name": ["null","io.logisticsos.types.DriverRef"], "name": "driver", "default": null },
    { "name": ["null","string"], "name": "driverPhone", "default": null },
    { "name": ["null",{"type":"long","logicalType":"timestamp-millis"}], "name": "etaPickup", "default": null }
  ]
}
```

### 3.5 `ShipmentTenderDeclined`

```json
{
  "type": "record",
  "name": "ShipmentTenderDeclined",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "tenderId", "type": "string" },
    { "name": "carrier", "type": "io.logisticsos.types.CarrierRef" },
    { "name": "reasonCode", "type": "string", "doc": "CAPACITY|RATE|SERVICE|EQUIPMENT|OTHER" },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null","string"], "name": "reasonText", "default": null },
    { "name": ["null",{"type":"long","logicalType":"timestamp-millis"}], "name": "declinedAt", "default": null }
  ]
}
```

### 3.6 `ShipmentDispatched`

| Field | Req/Opt | Type |
|---|---|---|
| shipmentId | Req | string |
| dispatchedAt | Req | timestamp |
| originLocation | Req | LocationRef |
| status | Req | ShipmentStatus |
| vehicle | Opt | VehicleRef |
| trailer | Opt | VehicleRef |
| driver | Opt | DriverRef |
| sealNumber | Opt | string |
| odometerStart | Opt | long |
| gpsProvider | Opt | string |

```json
{
  "type": "record",
  "name": "ShipmentDispatched",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "dispatchedAt", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "originLocation", "type": "io.logisticsos.types.LocationRef" },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null","io.logisticsos.types.VehicleRef"], "name": "vehicle", "default": null },
    { "name": ["null","io.logisticsos.types.VehicleRef"], "name": "trailer", "default": null },
    { "name": ["null","io.logisticsos.types.DriverRef"], "name": "driver", "default": null },
    { "name": ["null","string"], "name": "sealNumber", "default": null },
    { "name": ["null","long"], "name": "odometerStart", "default": null },
    { "name": ["null","string"], "name": "gpsProvider", "default": null }
  ]
}
```

### 3.7 `ShipmentPickedUp`

| Field | Req/Opt | Type |
|---|---|---|
| shipmentId | Req | string |
| pickedUpAt | Req | timestamp |
| location | Req | LocationRef |
| quantity | Req | Quantity |
| weightKg | Req | double |
| status | Req | ShipmentStatus |
| piecesScanned | Opt | int |
| condition | Opt | string |
| signatureRef | Opt | string |
| temperatureC | Opt | double |
| sealNumber | Opt | string |
| discrepancy | Opt | string |

```json
{
  "type": "record",
  "name": "ShipmentPickedUp",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "pickedUpAt", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "location", "type": "io.logisticsos.types.LocationRef" },
    { "name": "quantity", "type": "io.logisticsos.types.Quantity" },
    { "name": "weightKg", "type": "double" },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null","int"], "name": "piecesScanned", "default": null },
    { "name": ["null","string"], "name": "condition", "default": null },
    { "name": ["null","string"], "name": "signatureRef", "default": null },
    { "name": ["null","double"], "name": "temperatureC", "default": null },
    { "name": ["null","string"], "name": "sealNumber", "default": null },
    { "name": ["null","string"], "name": "discrepancy", "default": null }
  ]
}
```

### 3.8 `ShipmentArrivedAtFacility` / `ShipmentDepartedFacility`

Both share a shape; `arrivedAt`/`departedAt` differ.

| Field | Req/Opt | Type |
|---|---|---|
| shipmentId | Req | string |
| facility | Req | LocationRef |
| legIndex | Req | int |
| mode | Req | Mode |
| status | Req | ShipmentStatus |
| arrivedAt / departedAt | Req (per event) | timestamp |
| vehicle | Opt | VehicleRef |
| dock | Opt | LocationRef |
| dwellMinutes | Opt | int |

```json
{
  "type": "record",
  "name": "ShipmentFacilityEvent",
  "namespace": "io.logisticsos.shipment.v1",
  "doc": "Used by ShipmentArrivedAtFacility and ShipmentDepartedFacility payloads",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "facility", "type": "io.logisticsos.types.LocationRef" },
    { "name": "legIndex", "type": "int" },
    { "name": "mode", "type": "Mode" },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": "arrivedAt", "type": ["null", {"type":"long","logicalType":"timestamp-millis"}], "default": null },
    { "name": "departedAt", "type": ["null", {"type":"long","logicalType":"timestamp-millis"}], "default": null },
    { "name": ["null","io.logisticsos.types.VehicleRef"], "name": "vehicle", "default": null },
    { "name": ["null","io.logisticsos.types.LocationRef"], "name": "dock", "default": null },
    { "name": ["null","int"], "name": "dwellMinutes", "default": null }
  ]
}
```

### 3.9 `ShipmentOutForDelivery`

| Field | Req/Opt | Type |
|---|---|---|
| shipmentId | Req | string |
| outForDeliveryAt | Req | timestamp |
| vehicle | Req | VehicleRef |
| stopSequence | Req | int |
| estimatedDelivery | Req | timestamp |
| status | Req | ShipmentStatus |
| driver | Opt | DriverRef |
| deliveryWindow | Opt | TimeWindow |
| routePlanId | Opt | string |

```json
{
  "type": "record",
  "name": "ShipmentOutForDelivery",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "outForDeliveryAt", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "vehicle", "type": "io.logisticsos.types.VehicleRef" },
    { "name": "stopSequence", "type": "int" },
    { "name": "estimatedDelivery", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null","io.logisticsos.types.DriverRef"], "name": "driver", "default": null },
    { "name": ["null","io.logisticsos.types.TimeWindow"], "name": "deliveryWindow", "default": null },
    { "name": ["null","string"], "name": "routePlanId", "default": null }
  ]
}
```

### 3.10 `ShipmentDelivered` (POD)

| Field | Req/Opt | Type |
|---|---|---|
| shipmentId | Req | string |
| deliveredAt | Req | timestamp |
| deliveredLocation | Req | LocationRef |
| receivedBy | Req | string |
| podType | Req | enum PODType |
| quantity | Req | Quantity |
| status | Req | ShipmentStatus |
| signatureRef | Opt | string |
| photoRefs | Opt | array<string> |
| condition | Opt | string |
| geo | Opt | GeoPoint |
| note | Opt | string |
| temperatureC | Opt | double |
| discrepancy | Opt | string |

```json
{
  "type": "record",
  "name": "ShipmentDelivered",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "deliveredAt", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "deliveredLocation", "type": "io.logisticsos.types.LocationRef" },
    { "name": "receivedBy", "type": "string", "doc": "Name or party/ref of receiver" },
    { "name": "podType", "type": { "type": "enum", "name": "PODType",
        "symbols": ["SIGNATURE","PHOTO","SIGNATURE_PHOTO","ELECTRONIC","TOUCHLESS","NONE"] } },
    { "name": "quantity", "type": "io.logisticsos.types.Quantity" },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null","string"], "name": "signatureRef", "default": null },
    { "name": ["null",{"type":"array","items":"string"}], "name": "photoRefs", "default": null },
    { "name": ["null","string"], "name": "condition", "default": null },
    { "name": ["null","io.logisticsos.types.GeoPoint"], "name": "geo", "default": null },
    { "name": ["null","string"], "name": "note", "default": null },
    { "name": ["null","double"], "name": "temperatureC", "default": null },
    { "name": ["null","string"], "name": "discrepancy", "default": null }
  ]
}
```

### 3.11 `ShipmentPodDocumentAttached`

| Field | Req/Opt | Type |
|---|---|---|
| shipmentId | Req | string |
| documentId | Req | string |
| documentType | Req | enum | POD/SIGNATURE/PHOTO/CUSTOMS |
| uri | Req | string |
| status | Req | ShipmentStatus |
| capturedAt | Opt | timestamp |
| hash | Opt | string (integrity) |

```json
{
  "type": "record",
  "name": "ShipmentPodDocumentAttached",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "documentId", "type": "string" },
    { "name": "documentType", "type": {"type":"enum","name":"PodDocumentType",
        "symbols":["POD","SIGNATURE","PHOTO","CUSTOMS","WEIGHT_TICKET"]} },
    { "name": "uri", "type": "string" },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null",{"type":"long","logicalType":"timestamp-millis"}], "name": "capturedAt", "default": null },
    { "name": ["null","string"], "name": "sha256", "default": null }
  ]
}
```

### 3.12 `ShipmentExceptionRaised` / `ShipmentExceptionResolved`

| Field | Req/Opt | Type |
|---|---|---|
| shipmentId | Req | string |
| exceptionId | Req | string |
| type | Req | enum ExceptionType |
| severity | Req | enum Severity |
| status | Req | ShipmentStatus |
| raisedAt / resolvedAt | Req (per event) | timestamp |
| code | Opt | string |
| description | Opt | string |
| location | Opt | LocationRef |
| legIndex | Opt | int |
| resolutionCode | Opt | string |
| actions | Opt | array<string> |
| resolvedBy | Opt | string |

```json
{
  "type": "record",
  "name": "ShipmentException",
  "namespace": "io.logisticsos.shipment.v1",
  "doc": "Used by ShipmentExceptionRaised / ShipmentExceptionResolved",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "exceptionId", "type": "string" },
    { "name": "type", "type": { "type": "enum", "name": "ExceptionType",
        "symbols": ["DELAY","DAMAGE","SHORTAGE","OVERAGE","REFUSED","MISSING_DOC",
                    "TEMPERATURE_BREACH","CUSTOMS_HOLD","ACCIDENT","WEATHER","ADDRESS_ISSUE"] } },
    { "name": "severity", "type": { "type":"enum","name":"Severity",
        "symbols":["LOW","MEDIUM","HIGH","CRITICAL"] } },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": "raisedAt", "type": ["null",{"type":"long","logicalType":"timestamp-millis"}], "default": null },
    { "name": "resolvedAt", "type": ["null",{"type":"long","logicalType":"timestamp-millis"}], "default": null },
    { "name": ["null","string"], "name": "code", "default": null },
    { "name": ["null","string"], "name": "description", "default": null },
    { "name": ["null","io.logisticsos.types.LocationRef"], "name": "location", "default": null },
    { "name": ["null","int"], "name": "legIndex", "default": null },
    { "name": ["null","string"], "name": "resolutionCode", "default": null },
    { "name": ["null",{"type":"array","items":"string"}], "name": "actions", "default": null },
    { "name": ["null","string"], "name": "resolvedBy", "default": null }
  ]
}
```

### 3.13 `ShipmentCancelled`

```json
{
  "type": "record",
  "name": "ShipmentCancelled",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "cancelledAt", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "reasonCode", "type": "string", "doc": "CUSTOMER|CAPACITY|FRAUD|DUPLICATE|OTHER" },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null","string"], "name": "cancelledBy", "default": null },
    { "name": ["null","io.logisticsos.types.Money"], "name": "cancellationFee", "default": null }
  ]
}
```

### 3.14 `ShipmentReturned`

```json
{
  "type": "record",
  "name": "ShipmentReturned",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "returnedAt", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "returnLocation", "type": "io.logisticsos.types.LocationRef" },
    { "name": "reasonCode", "type": "string" },
    { "name": "quantity", "type": "io.logisticsos.types.Quantity" },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null","string"], "name": "returnAuthRef", "default": null }
  ]
}
```

### 3.15 `ShipmentSettled` (emitted by Billing)

| Field | Req/Opt | Type |
|---|---|---|
| shipmentId | Req | string |
| settlementId | Req | string |
| invoiceRef | Req | string |
| carrier | Req | CarrierRef |
| agreedCharge | Req | Money |
| settledAt | Req | timestamp |
| status | Req | ShipmentStatus |
| accessorials | Opt | array<Charge> |
| glPostingRef | Opt | string |
| payTerms | Opt | string |

```json
{
  "type": "record",
  "name": "ShipmentSettled",
  "namespace": "io.logisticsos.shipment.v1",
  "fields": [
    { "name": "shipmentId", "type": "string" },
    { "name": "settlementId", "type": "string" },
    { "name": "invoiceRef", "type": "string" },
    { "name": "carrier", "type": "io.logisticsos.types.CarrierRef" },
    { "name": "agreedCharge", "type": "io.logisticsos.types.Money" },
    { "name": "settledAt", "type": {"type":"long","logicalType":"timestamp-millis"} },
    { "name": "status", "type": "ShipmentStatus" },
    { "name": ["null",{"type":"array","items":{
        "type":"record","name":"Charge","fields":[
          {"name":"chargeType","type":"string"},
          {"name":"amount","type":"io.logisticsos.types.Money"},
          {"name":["null","string"],"name":"glAccount","default":null}
        ]}}], "name": "accessorials", "default": null },
    { "name": ["null","string"], "name": "glPostingRef", "default": null },
    { "name": ["null","string"], "name": "payTerms", "default": null }
  ]
}
```

## 4. Emitter / consumer responsibilities

| Service | Emits (shipment) | Consumes |
|---|---|---|
| **OMS** | `ShipmentCreated`, `ShipmentCancelled`, `ShipmentReturned` | all shipment events (order status sync) |
| **TMS** | Planned/Tendered/Accepted/Declined/Dispatched/PickedUp/Arrived/Departed/OutForDelivery/Delivered/PodDocument/Exception/Cancelled/Returned | `ShipmentCreated`, `orch.command.v1` (TenderShipment, Replan) |
| **WMS** | `ShipmentPickedUp` (scan at dock) | `orch.command.v1`, `ShipmentCancelled` |
| **YMS** | (yard events separately) | `ShipmentPlanned`, `ShipmentDispatched`, `ShipmentArrivedAtFacility` (appointments) |
| **Visibility** | milestone projections; may emit `Arrived/Departed` from geofence/carrier feeds | all shipment events |
| **Intelligence** | `intel.prediction.v1` (ETA), can propose `ShipmentExceptionRaised` | all shipment events |
| **Orchestrator** | `ShipmentCancelled` (policy), fixes; emits commands | all shipment events |
| **Billing** | `ShipmentSettled`, `fin.*` accruals | `ShipmentDelivered`, `ShipmentExceptionRaised/Resolved`, `ShipmentReturned` |
| **Analytics (L7)** | — | all shipment events (sink to lakehouse) |
| **Experience (L6)** | — | read models built from all shipment events |

## 5. Idempotent processing notes for shipments

- **Partition key:** `shipmentId` → total order per shipment; `aggregateVersion` is strictly increasing per shipment.
- **State store:** consumers keep `(shipmentId → lastAppliedVersion)`; ignore events with `version ≤ lastApplied` (stale/replay).
- **Effect store:** financial/inventory side effects insert ledger rows keyed by `idempotencyKey` (insert-or-ignore), then apply delta.
- **Status transitions:** validate the event's `previousStatus → status` against the [state machine](#1-lifecycle-state-machine); illegal transitions are quarantined to `dlq.tms.shipment.events.v1` rather than silently applied.
- **Replay:** safe at any time — projections rebuild from `tms.shipment.events.v1` retention (7y) because handlers are idempotent and version-guarded.
