# Reference Data Schemas

> Status: Accepted · Related: [common-types.md](common-types.md) · [shipment-events.md](shipment-events.md) · [../data-model.md](../data-model.md) · [../services.md](../services.md)

Reference entities (`Customer`, `Carrier`, `Vehicle`, `Location`) are **master data owned by MDM** and published as golden records on compacted topics. Events embed only lightweight [refs](common-types.md#31-lightweight-references); consumers resolve full records from the MDM read model by id.

Topics (compacted, partition key = entity id):
`mdm.customer.events.v1` · `mdm.carrier.events.v1` · `mdm.vehicle.events.v1` · `mdm.location.events.v1`

Each entity publishes `ReferenceUpserted` and `ReferenceDeactivated` events using the same payload below (state carried inline, `active` flag). Golden-record rules: single writer (MDM), unique business keys, survivorship on merge.

## 1. Customer

Topic: **`mdm.customer.events.v1`** · key = `customerId` · emitted by MDM · consumed by OMS, TMS, Billing, Orchestrator, Analytics, L6.

| Field | Req/Opt | Type | Notes |
|---|---|---|---|
| customerId | Req | string | ULID |
| tenantId | Req | string | scope |
| name | Req | string | legal/display |
| type | Req | enum | SHIPPER/BROKER/CONSIGNEE/B2B/RETAIL |
| status | Req | enum | ACTIVE/ONBOARDING/SUSPENDED/CREDIT_HOLD/CLOSED |
| roles | Req | array<string> | |
| billingAddress | Req | Address | |
| contacts | Req | array<Contact> | ≥1 |
| paymentTerms | Req | string | e.g. NET_30 |
| externalIds | Opt | array<ExternalRef> | |
| taxId | Opt | string | |
| duns | Opt | string | |
| creditLimit | Opt | Money | |
| serviceLevelAgreements | Opt | array<Sla> | |
| parentCustomerId | Opt | string | |
| glAccount | Opt | string | |
| updatedAt | Req | timestamp | |

```json
{
  "type": "record",
  "name": "Customer",
  "namespace": "io.logisticsos.mdm.v1",
  "fields": [
    { "name": "customerId", "type": "string" },
    { "name": "tenantId", "type": "string" },
    { "name": "name", "type": "string" },
    { "name": "type", "type": {"type":"enum","name":"CustomerType",
        "symbols":["SHIPPER","BROKER","CONSIGNEE","B2B","RETAIL"]} },
    { "name": "status", "type": {"type":"enum","name":"CustomerStatus",
        "symbols":["ACTIVE","ONBOARDING","SUSPENDED","CREDIT_HOLD","CLOSED"]} },
    { "name": "roles", "type": {"type":"array","items":"string"} },
    { "name": "billingAddress", "type": "io.logisticsos.types.Address" },
    { "name": "contacts", "type": {"type":"array","items":{
        "type":"record","name":"Contact","fields":[
          {"name":"name","type":"string"},
          {"name":["null","string"],"name":"email","default":null},
          {"name":["null","string"],"name":"phone","default":null},
          {"name":["null","string"],"name":"role","default":null}
        ]}}},
    { "name": "paymentTerms", "type": "string" },
    { "name": ["null",{"type":"array","items":"string"}], "name": "externalIds", "default": null },
    { "name": ["null","string"], "name": "taxId", "default": null },
    { "name": ["null","string"], "name": "duns", "default": null },
    { "name": ["null","io.logisticsos.types.Money"], "name": "creditLimit", "default": null },
    { "name": ["null",{"type":"array","items":{
        "type":"record","name":"Sla","fields":[
          {"name":"serviceLevel","type":"string"},
          {"name":"targetTransitHours","type":"int"},
          {"name":["null","double"],"name":"otdTarget","default":null}
        ]}}], "name": "serviceLevelAgreements", "default": null },
    { "name": ["null","string"], "name": "parentCustomerId", "default": null },
    { "name": ["null","string"], "name": "glAccount", "default": null },
    { "name": "updatedAt", "type": {"type":"long","logicalType":"timestamp-millis"} }
  ]
}
```

## 2. Carrier

Topic: **`mdm.carrier.events.v1`** · key = `carrierId` · emitted by MDM · consumed by TMS, Billing, Orchestrator, Visibility, Analytics.

| Field | Req/Opt | Type | Notes |
|---|---|---|---|
| carrierId | Req | string | |
| tenantId | Req | string | |
| name | Req | string | |
| status | Req | enum | ACTIVE/ONBOARDING/SUSPENDED/OFFBOARDED |
| modes | Req | array<Mode> | services offered |
| contact | Req | Contact | |
| scac | Opt | string | |
| mcNumber | Opt | string | |
| dotNumber | Opt | string | |
| serviceLevels | Opt | array<string> | |
| addresses | Opt | array<Address> | terminals |
| insurance | Opt | Insurance | |
| authorityStatus | Opt | string | |
| ediCapabilities | Opt | array<string> | 204/214/210/990 |
| apiCredentialsRef | Opt | string | secret vault ref (never inline) |
| performanceScore | Opt | double | 0–100 |
| fleetSize | Opt | int | |
| certifications | Opt | array<string> | |

```json
{
  "type": "record",
  "name": "Carrier",
  "namespace": "io.logisticsos.mdm.v1",
  "fields": [
    { "name": "carrierId", "type": "string" },
    { "name": "tenantId", "type": "string" },
    { "name": "name", "type": "string" },
    { "name": "status", "type": {"type":"enum","name":"CarrierStatus",
        "symbols":["ACTIVE","ONBOARDING","SUSPENDED","OFFBOARDED"]} },
    { "name": "modes", "type": {"type":"array","items":"io.logisticsos.shipment.v1.Mode"} },
    { "name": "contact", "type": "Contact" },
    { "name": ["null","string"], "name": "scac", "default": null },
    { "name": ["null","string"], "name": "mcNumber", "default": null },
    { "name": ["null","string"], "name": "dotNumber", "default": null },
    { "name": ["null",{"type":"array","items":"string"}], "name": "serviceLevels", "default": null },
    { "name": ["null",{"type":"array","items":"io.logisticsos.types.Address"}], "name": "addresses", "default": null },
    { "name": ["null",{"type":"record","name":"Insurance","fields":[
        {"name":"policyNumber","type":"string"},
        {"name":"coverage","type":"io.logisticsos.types.Money"},
        {"name":"expiresAt","type":"long","logicalType":"timestamp-millis"}]}],
      "name": "insurance", "default": null },
    { "name": ["null","string"], "name": "authorityStatus", "default": null },
    { "name": ["null",{"type":"array","items":"string"}], "name": "ediCapabilities", "default": null },
    { "name": ["null","string"], "name": "apiCredentialsRef", "default": null, "doc": "vault path; never the secret" },
    { "name": ["null","double"], "name": "performanceScore", "default": null },
    { "name": ["null","int"], "name": "fleetSize", "default": null },
    { "name": ["null",{"type":"array","items":"string"}], "name": "certifications", "default": null }
  ]
}
```

## 3. Vehicle

Topic: **`mdm.vehicle.events.v1`** · key = `vehicleId` · emitted by MDM · consumed by TMS, YMS, Visibility, Intelligence, Analytics.

| Field | Req/Opt | Type | Notes |
|---|---|---|---|
| vehicleId | Req | string | |
| tenantId | Req | string | |
| status | Req | enum | ACTIVE/IN_MAINTENANCE/RETIRED/RESERVED |
| type | Req | enum | TRUCK/VAN/TRAILER/CONTAINER/BIKE/VESSEL/RAIL |
| ownership | Req | enum | OWNED/LEASED/PARTNER/CONTRACTOR |
| carrier | Opt | CarrierRef | if operated by a carrier |
| vin | Opt | string | |
| licensePlate | Opt | string | |
| equipmentType | Opt | string | dry van/reefer/flatbed |
| maxWeightKg | Opt | double | |
| capacity | Opt | Capacity | dims/volume/pallets |
| tempCapable | Opt | boolean | |
| tempRange | Opt | TemperatureRange | |
| telematicsProvider | Opt | string | |
| currentLocation | Opt | LocationRef | |
| homeLocation | Opt | LocationRef | |
| lastTelemetryAt | Opt | timestamp | |
| odometerKm | Opt | long | |

```json
{
  "type": "record",
  "name": "Vehicle",
  "namespace": "io.logisticsos.mdm.v1",
  "fields": [
    { "name": "vehicleId", "type": "string" },
    { "name": "tenantId", "type": "string" },
    { "name": "status", "type": {"type":"enum","name":"VehicleStatus",
        "symbols":["ACTIVE","IN_MAINTENANCE","RETIRED","RESERVED"]} },
    { "name": "type", "type": {"type":"enum","name":"VehicleType",
        "symbols":["TRUCK","VAN","TRAILER","CONTAINER","BIKE","VESSEL","RAIL"]} },
    { "name": "ownership", "type": {"type":"enum","name":"OwnershipType",
        "symbols":["OWNED","LEASED","PARTNER","CONTRACTOR"]} },
    { "name": ["null","io.logisticsos.types.CarrierRef"], "name": "carrier", "default": null },
    { "name": ["null","string"], "name": "vin", "default": null },
    { "name": ["null","string"], "name": "licensePlate", "default": null },
    { "name": ["null","string"], "name": "equipmentType", "default": null },
    { "name": ["null","double"], "name": "maxWeightKg", "default": null },
    { "name": ["null",{"type":"record","name":"Capacity","fields":[
        {"name":["null","double"],"name":"volumeM3","default":null},
        {"name":["null","int"],"name":"palletPositions","default":null},
        {"name":["null","double"],"name":"lengthM","default":null},
        {"name":["null","double"],"name":"widthM","default":null},
        {"name":["null","double"],"name":"heightM","default":null}]}],
      "name": "capacity", "default": null },
    { "name": ["null","boolean"], "name": "tempCapable", "default": null },
    { "name": ["null","io.logisticsos.types.TemperatureRange"], "name": "tempRange", "default": null },
    { "name": ["null","string"], "name": "telematicsProvider", "default": null },
    { "name": ["null","io.logisticsos.types.LocationRef"], "name": "currentLocation", "default": null },
    { "name": ["null","io.logisticsos.types.LocationRef"], "name": "homeLocation", "default": null },
    { "name": ["null",{"type":"long","logicalType":"timestamp-millis"}], "name": "lastTelemetryAt", "default": null },
    { "name": ["null","long"], "name": "odometerKm", "default": null }
  ]
}
```

> **Note:** `TemperatureRange` referenced from `io.logisticsos.types` is defined in [shipment-events.md](shipment-events.md#31-shipmentcreated--order-placement) (shared namespace). Align the namespace when generating code.

## 4. Location

Topic: **`mdm.location.events.v1`** · key = `locationId` · emitted by MDM · consumed by all domains, Intelligence, Analytics.

| Field | Req/Opt | Type | Notes |
|---|---|---|---|
| locationId | Req | string | |
| tenantId | Req | string | |
| name | Req | string | |
| type | Req | enum | WAREHOUSE/DC/DOCK/STORE/PORT/CUSTOMER_SITE/TERMINAL/RAIL_YARD |
| address | Req | Address | |
| timezone | Req | string | IANA tz |
| status | Req | enum | ACTIVE/INACTIVE/SEASONAL |
| operatingHours | Opt | array<HoursOfOperation> | |
| dockCount | Opt | int | |
| appointmentRequired | Opt | boolean | |
| capabilities | Opt | array<string> | cross-dock/reefer/hazmat |
| contact | Opt | Contact | |
| parentLocationId | Opt | string | |
| geoFenceRadiusM | Opt | int | |

```json
{
  "type": "record",
  "name": "Location",
  "namespace": "io.logisticsos.mdm.v1",
  "fields": [
    { "name": "locationId", "type": "string" },
    { "name": "tenantId", "type": "string" },
    { "name": "name", "type": "string" },
    { "name": "type", "type": {"type":"enum","name":"LocationType",
        "symbols":["WAREHOUSE","DC","DOCK","STORE","PORT","CUSTOMER_SITE","TERMINAL","RAIL_YARD"]} },
    { "name": "address", "type": "io.logisticsos.types.Address" },
    { "name": "timezone", "type": "string" },
    { "name": "status", "type": {"type":"enum","name":"LocationStatus",
        "symbols":["ACTIVE","INACTIVE","SEASONAL"]} },
    { "name": ["null",{"type":"array","items":{
        "type":"record","name":"HoursOfOperation","fields":[
          {"name":"dayOfWeek","type":"int"},
          {"name":"openMinuteOfDay","type":"int"},
          {"name":"closeMinuteOfDay","type":"int"}]}}],
      "name": "operatingHours", "default": null },
    { "name": ["null","int"], "name": "dockCount", "default": null },
    { "name": ["null","boolean"], "name": "appointmentRequired", "default": null },
    { "name": ["null",{"type":"array","items":"string"}], "name": "capabilities", "default": null },
    { "name": ["null","Contact"], "name": "contact", "default": null },
    { "name": ["null","string"], "name": "parentLocationId", "default": null },
    { "name": ["null","int"], "name": "geoFenceRadiusM", "default": null }
  ]
}
```

## 5. Golden-record & reference events

Each entity publishes a common wrapper so MDM can signal create/update/deactivate:

```json
{
  "type": "record",
  "name": "ReferenceUpserted",
  "namespace": "io.logisticsos.mdm.v1",
  "doc": "Wraps any reference record; deactivation sets active=false",
  "fields": [
    { "name": "entityType", "type": {"type":"enum","name":"RefEntityType",
        "symbols":["CUSTOMER","CARRIER","VEHICLE","LOCATION"]} },
    { "name": "record", "type": "bytes", "doc": "Serialized Customer|Carrier|Vehicle|Location" },
    { "name": "active", "type": "boolean" },
    { "name": "sourceSystem", "type": "string", "doc": "where the change originated (IDP/system)" },
    { "name": "mergedFrom", "type": ["null",{"type":"array","items":"string"}], "default": null,
      "doc": "ids merged into this golden record (survivorship)" }
  ]
}
```

## 6. Consumption & idempotency (reference data)

- **Compacted topics:** `cleanup.policy=compact`; consumers hold latest golden record keyed by entity id.
- **Idempotency:** upsert by `entityId`; the envelope `aggregateVersion` (MDM record version) guards against stale applies. Merges emit `mergedFrom`; consumers re-point foreign keys idempotently.
- **Caching:** services cache golden records locally (TTL + invalidation on `ReferenceUpserted`); events still carry lightweight refs so a cache miss never blocks processing.
- **PII/residency:** `Customer` and `Vehicle` may contain PII; tagged and region-pinned ([cross-cutting.md §1, §4](../cross-cutting.md)).
