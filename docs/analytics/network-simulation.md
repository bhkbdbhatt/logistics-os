# Network-Level Simulation (Analytics Workbench)

> Status: Accepted · Related: [README.md](README.md) · [../intelligence/network-simulation.md](../intelligence/network-simulation.md) · [../schemas/intelligence-events.md](../schemas/intelligence-events.md) · [../adr/0015-digital-twin-network-simulation.md](../adr/0015-digital-twin-network-simulation.md)

The **what-if workbench**: planners define scenarios against a baseline snapshot, and the digital twin reports the **cost / SLA / capacity** impact before committing real changes. The simulation **engine** is the digital twin described in [intelligence/network-simulation.md](../intelligence/network-simulation.md); this doc specifies the **input parameters, output metrics, and how results are surfaced** in the control tower / analytics layer.

## 1. Input: what-if parameters

A scenario is a declarative set of **levers** over a horizon (`SimulationRequested`, `intel.simulation.v1`). Grouped by intent:

### 1.1 Demand
| Parameter | Meaning |
|---|---|
| Volume change | % up/down overall, or per lane/customer/node |
| Demand shift | move volume between lanes/nodes/modes |
| New/enter/exit customer | structural demand change |
| Seasonality / peak profile | temporal shape |

### 1.2 Capacity & resources
| Parameter | Meaning |
|---|---|
| Fleet capacity | add/remove vehicles or equipment on a lane |
| Dock / door capacity | change doors or turnaround at a node |
| Labor | headcount/availability at nodes (pickers, drivers) |
| Node open/close | enable/disable DC, cross-dock, or yard |
| Contracted volume | raise/lower committed carrier capacity |

### 1.3 Network design
| Parameter | Meaning |
|---|---|
| Lane add/close | new corridor or closure (border/road/mode) |
| Modal shift | truck ↔ rail ↔ air ↔ ocean |
| Cross-dock vs stock | flow strategy change |
| Service area change | add/remove ZIP/postcode coverage |

### 1.4 Cost parameters
| Parameter | Meaning |
|---|---|
| Fuel / energy | fuel price change |
| Carrier rates | contract/spot rate change |
| Labor rate | wage change |
| Accessorials | surcharge changes |

### 1.5 Service policy
| Parameter | Meaning |
|---|---|
| SLA target | tighten/relax promised windows |
| Cutoffs | order/pick cutoffs |
| Priority rules | customer/service tiers |

### 1.6 Disruption (stress test)
| Parameter | Meaning |
|---|---|
| Lane/node outage | simulate closure for a window |
| Carrier loss | remove a carrier (falloff) |
| Weather / congestion | degrade transit on a region |
| Labor shortage | reduce staffing |

Plus: **horizon** (start/end), **objective** (`MIN_COST`, `MAX_SERVICE`, `MIN_EMISSIONS`, …), **Monte Carlo runs**, random **seed** (reproducibility), and **baseline snapshot id**.

## 2. Output: impact metrics

Results (`SimulationCompleted`, `intel.simulation.v1`) are always reported **vs baseline**, as distributions (P10/P50/P90), with a fidelity note.

### 2.1 Cost impact
- Total cost delta (abs + %) and **fixed vs variable** split
- Cost per shipment / per unit / per mile delta
- Cost-to-serve by lane/node/customer
- Expected change in accessorials/spot premium

### 2.2 SLA / service impact
- OTD% and OTP% delta (P10/P50/P90)
- On-time-to-promise delta; promise adherence
- Transit time distribution shift; late-shipment incidence
- Service-risk concentration (which lanes/customers degrade most)

### 2.3 Capacity impact
- Utilization by fleet/dock/labor/node
- Bottlenecks and overflow points; capacity headroom vs peak
- Queueing/congestion indicators; unfulfilled demand under constraints
- Sensitivity: which constraint binds first

### 2.4 Other
- Emissions delta (% and per ton-mile)
- Risk band / worst-case under destructive scenarios
- **Tornado/sensitivity** ranking of parameters by impact

## 3. Surfacing in the control tower

```
Scenario workbench
┌ Define baseline ───────────────┐  ┌ Compare ──────────────────────────────┐
│ snapshot: NET-2026-10-10       │  │          Baseline   Scenario    Δ       │
│ horizon: 30d  objective: COST  │  │ Cost     $4.2M      $3.9M     −7.1%    │
├ Adjust levers ─────────────────┤  │ OTD      97.8%      96.1%     −1.7pt   │
│ demand +15% on LANE-A          │  │ Util     82%        91%       +9pt     │
│ close node N-07                │  │ Emission 1.00       0.93      −7%      │
│ fuel +8%                       │  └───────────────────────────────────────┘
│ ▸ run (Monte Carlo 500)        │  Tornado: cost←fuel, OTD←node-close, ...
└────────────────────────────────┘  Export → planning / procurement / finance
```

- **Baseline vs scenario** side-by-side with explicit deltas and risk bands.
- **Association with policy**: results inform **offline** threshold/network tuning (never drive live control) ([ADR-0015](../adr/0015-digital-twin-network-simulation.md)).
- **Reproducibility**: seed + baseline snapshot + scenario JSON captured in `artifactRef`; re-runnable.
- Results flow to Analytics (L7) and planning; a summary tile can appear in the control tower, clearly labeled **simulated**.

## 4. Execution & cadence

| Aspect | Design |
|---|---|
| Trigger | on-demand from workbench; scheduled nightly baseline refresh |
| Engine | L4 discrete-event + Monte Carlo, reusing production constraints/solvers |
| Latency | typical scenario < 30 min; large Monte Carlo async (< 12 h) |
| Baseline freshness | daily |
| Calibration | monthly / post-major-change; fidelity (sim vs actual OTD error) reported |

## 5. SLAs

| Metric | Target |
|---|---|
| Workbench scenario completion | < 30 min (typical) |
| Async large run | < 12 h |
| Baseline snapshot freshness | daily |
| Delta reproduction (same seed) | exact |
| Fidelity (simulated vs actual OTD) | reported; target < 5 pts on mature networks |
