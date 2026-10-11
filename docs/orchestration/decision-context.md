# Decision Context & Policy Routing

> Status: Accepted · Related: [README.md](README.md) · [workflow-engine.md](workflow-engine.md) · [autonomous-agents.md](autonomous-agents.md) · [escalation.md](escalation.md) · [../schemas/orchestration-events.md](../schemas/orchestration-events.md) · [../adr/0012-policy-based-human-in-the-loop.md](../adr/0012-policy-based-human-in-the-loop.md)

The **Decision Context** is the single object from which every orchestration decision is made. It is assembled per sub-decision, evaluated by policy, and stored verbatim with the resulting `DecisionRecord` so any decision is reproducible and explainable.

Schema: [`DecisionContext`](../schemas/orchestration-events.md#5-decision-context--the-core-object).

## 1. Construction

```
read models (L2)          AI signals (L4)              policy (OPA)
   facts        ─┐        predictions/recs   ─┐        constraints ─┐
                 ├──▶  Context Assembler  ◀───┴─────────────────────┘
   subject refs ─┘             │
                               ▼
                        DecisionContext  ──▶ Policy Engine ──▶ autonomy + options
```

The **Context Assembler**:

1. Resolves `SubjectRefs` from the trigger (`shipmentId`, `loadId`, `orderId`, `nodeId`, locations).
2. Snapshots **facts** from CQRS read models (`shipment_view`, `inventory_view`, `yard_view`, `exception_view`) with `asOf` timestamps.
3. Attaches **signals** from L4 (`intel.prediction.v1`, `intel.recommendation.v1`) — each with `modelId`, `confidence`, `featuresHash`.
4. Derives **constraints** from SLA/MDM/policy (hard vs soft).
5. Generates **options** by combining playbooks + L4 recommendations + human prior art.
6. Pins `policyRef` (`policyId`, `version`, `tenantId`) and the `autonomyTier`.

Assembly is a pure, bounded operation with a latency SLO (< 3 s p99). It never calls L3 databases — only L2 read models and L4 APIs ([integrations.md §6](../integrations.md)).

## 2. Field semantics

| Field | Meaning | Used by |
|---|---|---|
| `facts` | snapshot of current state (locations, windows, inventory, labor, yard) | options, policy guards |
| `signals` | model outputs with confidence | option scores, confidence gate |
| `constraints[]` | SLA / capacity / cost / policy / regulatory / time / resource; `hard=true` is inviolable | option filtering, routing |
| `options[]` | candidate actions with expected outcome, cost, time, risk, confidence, source | router, human UI |
| `recommendedOptionId` | highest-scoring allowed option | default for AUTO; suggested to human |
| `policyRef` | exact policy version applied | audit, reproducibility |
| `autonomyTier` | L0–L3 for this decision | routing |
| `costBudget`, `timeLimitMin`, `deadline` | envelope for automation | budget/confidence gate |

## 3. Option scoring

Each `DecisionOption` carries a composite `score`:

```
score = w_service * serviceGain        # on-time, SLA protection
      + w_cost    * (1 - normCost)     # lower cost preferred
      + w_time    * (1 - normTime)
      - w_risk    * riskScore          # e.g., damage/detention probability
      + w_conf    * confidence         # prefer well-understood options
```

Weights (`w_*`) and normalization are **tenant policy**, selectable by `ReoptObjective` (`MIN_COST`, `MIN_TIME`, `MAX_SERVICE`, `BALANCE_COST_SERVICE`, `MIN_EMISSIONS`). Options failing a `hard` constraint or `policyAllowed=false` are excluded.

## 4. Policy evaluation & autonomy tiers

Policy (OPA) answers three questions per decision:

1. **May it be automated?** → `autonomyTier`
2. **Which options are allowed?** → `policyAllowed`, hard constraints
3. **Under what envelope?** → auto confidence threshold, cost budget, time limit

### 4.1 Autonomy tiers

| Tier | Meaning | Routing | Example default |
|---|---|---|---|
| `L0_FULL_AUTO` | fully automated, no notification | `AUTO` | proactive ETA notification |
| `L1_AUTO_NOTIFY` | automated + notify a human after the fact | `AUTO` | labor re-allocation, slot re-assignment |
| `L2_HUMAN_APPROVE` | automated proposal, human must approve | `HUMAN` | carrier re-booking, reroute on committed load |
| `L3_HUMAN_ONLY` | system only assembles context; human decides | `HUMAN` | hazmat/regulated, customer contract breach, high cost |

### 4.2 Routing decision tree

Diagram: [`diagrams/decision-routing.mmd`](diagrams/decision-routing.mmd).

```
if not policyPermitsAutomation:            route HUMAN (L3)
elif confidence < autoThreshold:           route HUMAN
elif costImpact > autoBudget:              route HUMAN
elif regulated or safety or hazmat:        route HUMAN
elif option.requiresPartnerAck:            route PARTNER
elif autonomyTier in {L0, L1}:             route AUTO   (L1 also notifies)
else:                                      route HUMAN (L2)
```

Thresholds (`autoThreshold`, `autoBudget`) are per tenant + decision type + severity.

## 5. Decision outcomes

| Event | When | Carries |
|---|---|---|
| `DecisionRequested` | context ready; routed | route, assignee, `slaDeadline`, full context |
| `DecisionMade` | auto or human/partner decided | `chosenOptionId`, `decidedByType`, `overrodeRecommendation`, `rationale`, `policyRef` |
| `DecisionDeferred` | more info / re-opt / external signal pending | `reason`, optional `reoptimizationId`, `retryAfter` |

The `DecisionMade` payload (plus the pinned `DecisionContext`) **is** the `DecisionRecord` — persisted to the Decision/Audit Store. Idempotent: replays are ignored if a record for `decisionId` exists.

## 6. Human-in-the-loop contract

- Human tasks are created only for `AWAITING_HUMAN`/`AWAITING_PARTNER`, with: the context, ranked options, recommended option, deadline, and a one-click default.
- Humans may **choose any `policyAllowed` option** or propose a new one (`source=HUMAN`), which is recorded as `overrodeRecommendation=true` when different.
- Every human task has an **SLA timer** → [escalation.md](escalation.md).
- The human UI is served by the ops BFF (`control-tower`), reading `exception_view` + the context; it contains no business logic ([services.md §3](../services.md)).

## 7. Explainability

For any decision, the audit store can reconstruct:

- **What** was decided (`DecisionMade`) and by whom (auto/human/partner).
- **Why**: the exact `facts` seen, `signals` (with model versions + confidence), ranked `options`, and `policyRef` version.
- **Counterfactuals**: scores of non-chosen options.

This satisfies the "reproducible decision" NFR ([README.md §5](README.md)) and feeds policy tuning + ML training on human overrides.

## 8. Worked mini-example

A `LATE_INBOUND` trigger fans out to four sub-decisions:

| Sub-decision | Confidence | Cost impact | Policy | Route | Result |
|---|---|---|---|---|---|
| `CUSTOMER_NOTIFY` | — | $0 | L0 | AUTO | proactive ETA sent |
| `LABOR_REALLOCATION` | 0.91 | low | L1 | AUTO + notify | shift plan adjusted |
| `REOPTIMIZE` (load) | — | low | L1 | AUTO → solver | recommendations ranked |
| `CARRIER_REBOOK` | 0.74 | high | L2 | HUMAN (SLA 15m) | supervisor approves re-tender |

Each decision has its own `DecisionContext`, `DecisionRecord`, and timer; the case joins them at `VERIFYING`. Full timing in [exception-flow.md](exception-flow.md).
