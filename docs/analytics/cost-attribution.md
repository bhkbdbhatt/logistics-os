# Shipment-Level Cost Attribution

> Status: Accepted · Related: [README.md](README.md) · [control-tower.md](control-tower.md) · [../integrations.md](../integrations.md) · [../schemas/analytics-events.md](../schemas/analytics-events.md) · [../data-model.md](../data-model.md)

Attributes **every cost — fixed and variable — to each shipment in real time**, so the control tower can show running cost-to-serve and margin while a shipment is still moving. Output is `analytics.cost.attribution.v1` ([schema](../schemas/analytics-events.md)). Billing remains the **system of record for charges**; attribution is a derived, reconcilable projection.

## 1. Cost taxonomy

| Class | Examples | How attributed |
|---|---|---|
| **Variable — direct** | linehaul, fuel, accessorials, last-mile, parcel rate, per-pick labor, packaging, payment fees | **Traced** to the shipment/order by key (shipmentId, loadId, orderId, tracking#) |
| **Variable — allocated** | shared linehaul on a consolidated load, shared trailer/reefer power, multi-stop fuel, shared dunnage | Allocated by a **driver** (mile, weight, cube, pallet, stop) |
| **Fixed — allocated** | facility & dock depreciation, WMS/automation amortization, salaried/indirect labor, fleet fixed, insurance, allocated G&A/overhead | Allocated via **activity-based costing (ABC)** at a **standard rate**, trued-up in batch |

## 2. Why real-time needs standard rates

True fixed cost per shipment is only known after the period closes (volumes, actual overhead). So real-time attribution uses a **standard cost rate** per driver (e.g., `$ / shipment`, `$ / mile`, `$ / pallet`, `$ / pick`), derived from the last closed period and forecast volume. As actuals arrive, the attribution is **trued up** in near-real-time/batch. The tower therefore shows an **estimate with an explicit status**, never a silent guess.

**Status progression** ([schema](../schemas/analytics-events.md)):

`TENTATIVE` (at plan/dispatch) → `REFINED` (at POD, more actuals) → `TRUED_UP` (invoice/timesheet/rate actuals) → `FINAL` (period close).

## 3. Allocation model

```
cost(shipment) = Σ direct_traced
               + Σ driver_allocated   (variable shared)
               + Σ standard_rate × driver_qty   (fixed pools)
```

### 3.1 Direct (traced)
Join canonical events by key: carrier invoice lines (`fin.bill`), rate-card computations, fuel by lane/mile, accessorial events, per-order packaging, per-pick labor where pick↔order is known.

### 3.2 Allocated variable
For costs shared across a load/route, allocate by the natural driver:

| Shared cost | Driver |
|---|---|
| Linehaul on a multi-stop/consolidated load | stop count, mileage, weight, or cube (policy per lane) |
| Fuel / reefer power | miles, hours, or temperature class |
| Trailer/equipment time | hours on load |
| Multi-drop delivery labor | stops, units, or lines |

Allocation must be **additive** (shares sum to the pool) and **stable** (same inputs → same split) so numbers don't jump.

### 3.3 Fixed (ABC pools → standard rates)
Define **cost pools** (per facility, per fleet, per function) and a **cost driver** for each; compute a rate = `pool / driver volume` from the last closed period; apply `rate × shipment driver qty`. Examples: `$ / outbound shipment` for facility overhead, `$ / pallet handled` for MHE, `$ / order line` for salaried front-office. Rates refresh on a schedule (daily/weekly) and are versioned.

## 4. Real-time pipeline

Diagram: [`diagrams/cost-attribution.mmd`](diagrams/cost-attribution.mmd).

| Step | Trigger | Tier | Latency budget |
|---|---|---|---|
| Tentative attribution at plan/dispatch (standard rates + planned direct) | `tms.shipment.events.v1` planned/dispatched | T0 | p99 < 2 s |
| Refine at milestones/POD (actual miles, stops, weight, labor) | `vis.milestone.events.v1`, WMS actuals | T1/T2 | p99 < 30 s (T1) / < 15 min (T2) |
| True-up on carrier invoice / timesheet / fuel actuals | `fin.bill`, HR/labor, fuel feeds | T3 | nightly (invoice aging = T2 alerts) |
| Final at period close | finance close | T3 | T+1 / close SLA |

Each update **supersedes** the prior record for the shipment (status monotonic), so consumers keep the highest-status version.

## 5. Data sources

| Source | Provides |
|---|---|
| `tms.shipment.events.v1`, `tms.load.events.v1` | legs, miles, stops, equipment, mode |
| `fin.charge.events.v1`, `fin.bill.events.v1` | rate-card charges, carrier invoice lines, accessorials |
| `vis.milestone.events.v1` | actual timestamps, dwell, stops made |
| `wms.task.events.v1`, `inv.inventory.events.v1` | picks, units, pallets, packaging |
| Labor/HR + timekeeping | direct & indirect labor |
| Fixed-asset/finance feeds | depreciation, insurance, overhead pools |
| MDM (`Vehicle`, `Location`, `Lane`, `Carrier`) | driver selection, facility assignment |

## 6. Worked example (consolidated LTL)

```
Shipment SHP-9812 (2 pallets, 400 kg, on a 4-stop load, 210 mi, 3 picks)
  Direct:   linehaul share (traced by load)          $180.00
  Alloc:    fuel 210mi × $0.42/mi × share(2/8 pal)   $  22.05
  Alloc:    delivery labor 1 stop × std $18          $  18.00
  Fixed:    facility overhead 2 pallets × $3.10      $   6.20
  Fixed:    MHE 3 picks × $0.85                      $   2.55
  ─────────────────────────────────────────────
  Variable: $202.05   Fixed: $8.75   Total: $210.80
  Status: REFINED (at POD)   → trued-up on invoice → FINAL
```

## 7. Rules & edge cases

- **Consolidated loads:** allocate shared linehaul by the lane's configured driver; never double-count a direct charge that is already per-shipment.
- **Returns / re-delivery:** attributed to the return shipment, with a link to the original for cost-to-serve roll-ups.
- **Multi-leg / multimodal:** sum per-leg components; attribute handling/transfer costs to the node that incurs them.
- **Shared equipment & demurrage:** allocate by time-on-load; demurrage attributed to the party/cause, flagged for recovery.
- **Additivity invariant:** for any load/period, `Σ shipment costs = pool totals` (± rounding). Enforced by a reconciliation job.
- **Currency:** all amounts in the tenant's reporting currency (Money minor units + ISO-4217); FX applied at a versioned rate snapshot.
- **No double counting with Billing:** attribution is a **shadow** of Billing, reconciled nightly; variances > tolerance raise `COST_OVERRUN`/ops alerts.

## 8. SLAs

| Metric | Target |
|---|---|
| Tentative attribution after dispatch | p99 < 2 s |
| Refined after POD | p99 < 30 s |
| Intraday cost-to-serve freshness | < 15 min |
| Nightly true-up | complete by 06:00 local |
| Reconciliation variance vs Billing | < 0.5% of total cost |
| Additivity | exact (± rounding) |
