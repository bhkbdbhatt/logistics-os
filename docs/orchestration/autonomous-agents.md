# Autonomous Agents in the Orchestration Layer

> Status: Accepted · Decision: [ADR-0018](../adr/0018-autonomous-agent-architecture.md) · Related: [README.md](README.md) · [decision-context.md](decision-context.md) · [workflow-engine.md](workflow-engine.md) · [exception-flow.md](exception-flow.md) · [../schemas/orchestration-events.md](../schemas/orchestration-events.md) · [../adr/0012-policy-based-human-in-the-loop.md](../adr/0012-policy-based-human-in-the-loop.md) · [../adr/0015-digital-twin-network-simulation.md](../adr/0015-digital-twin-network-simulation.md)

## 0. What an "agent" is here (and is not)

An **agent** is a stateful reasoner inside L5 that is woken by the Cascade Orchestrator, perceives via a **bounded DecisionContext**, proposes an **ordered set of typed options (a plan)**, and — only if policy permits — executes them as idempotent commands. It is *not* a free-roaming LLM loop with environment access.

| An agent… | Agree | Because |
|---|---|---|
| perceives only a pre-assembled context | ✅ | reproducible decisions ([decision-context.md §5](decision-context.md)) |
| proposes options, never touches L3 DBs | ✅ | [services.md §3](../services.md) |
| is gated by OPA at context-time AND execution-time | ✅ | defense in depth |
| leaves an auditable `DecisionRecord` + plan trace | ✅ | NFR "every decision reproducible" ([nfr.md §7](../nfr.md)) |
| scrapes DBs / trades / emails APIs freely | ❌ | unbounded blast radius, un-auditable |
| writes domain state directly | ❌ | that is L3's job; agents command, systems act |

Reasoning engine is **hybrid**: deterministic policy/solver by default; an **optional pinned LLM reasoner** for triage, summarization, novel-option generation and natural-language rationale. LLM presence is a per-tenant/per-case toggle — no mandatory LLM dependency on any safety-critical path. Rationale for the design: [ADR-0018](../adr/0018-autonomous-agent-architecture.md).

## 1. Placement & runtime

```
  L1–L4 events ──▶ Trigger Evaluator ──▶ Cascade Orchestrator (Temporal)
                                            │  spawn
                                            ▼
                              ┌─────────────────────────────┐
                              │  Agent Runtime (L5)          │
                              │  one worker per (domain,     │
                              │   site/tenant) — single-     │
                              │   threaded per subject       │
                              └───────┬──────────┬──────────┘
                    Context Assembler │          │
                       (read models + │)         │
                        L4 signals ──▶▼          ▼
                              ┌─────────────┐ ┌──────────────────┐
                              │ Reasoner    │ │ Digital-twin     │
                              │ policy/LLM  │ │ simulation (L4)  │
                              └──────┬──────┘ │ [ADR-0015]       │
                                     ▼        └──────────────────┘
                              ┌─────────────────────────────┐
                              │ plan = ordered DecisionOpts │
                              └──────┬──────────────────────┘
                                     ▼  OPA re-check (exec time)
                              ┌─────────────────────────────┐
                              │ Action Executor (commands)   │
                              └─────────────────────────────┘
    every step ↦ DecisionContext + DecisionMade + plan trace (Audit Store)
```

- Workers are **deterministic-durable**: kill/restart restores from the last `DecisionRecord`; no state is held only in the model.
- **One agent instance per subject** (`caseId`) — re-triggers update the open plan, never fork a twin ([ADR-0013](../adr/0013-exception-cascade-and-reopt-coalescing.md)).
- **Agent catalog (per-tenant, versioned):** `warehouseAgent` (per DC/site), `yardAgent`, `loadPlanner` (network), `carrierAgent`, `promiseAgent` (ATP/ETA), `commsAgent` (customer notifications). Scale by sites/tenants, **not** per shipment.

## 2. (a) What may be automated vs. human-in-the-loop

Autonomy is decided per **decision archetype**, not per flavor-of-the-week. Policy (OPA) evaluates these **criteria** and their thresholds:

| Criterion | Favors AUTO | Favors HITL | Typical threshold |
|---|---|---|---|
| Reversibility | fully reversible (re-sequence, reschedule internal) | irreversible / externally committed | plan ≤ 2 compensating steps ⇒ auto |
| Money / blast radius | $0–low | high | cost cap per decision (tenant-set) |
| External visibility | internal-only | tenant-visible promise / contract / partner ack | anything touching `promise` or `contract` ⇒ ≥ L2 |
| Regulatory / safety | none | hazmat, regulated goods, injury potential | any ⇒ L3 |
| Option convergence | one clearly-dominant answer | many plausible, judgment-dependent | score gap ≥ threshold ⇒ auto |
| Confidence | high (L4 sign, sim yield) | low | conf ≥ autoThreshold (per type) |
| Velocity needed | seconds-scale | minutes ok | e.g., < 5 min to deadline |

| Archetype | Default tier | Trying example | P |
|---|---|---|---|
| Proactive notifications | `L0_FULL_AUTO` | ETA update when committed promise still met | **P0** |
| Internal re-sequencing within capacity | `L1_AUTO_NOTIFY` | re-order pick waves, shift labor ≤ budget, slot re-assignment | **P0** |
| Informational partner/carrier updates | `L1_AUTO_NOTIFY` | notify carrier of slip (no re-negotiation) | **P1** |
| Sim-validated re-plans inside a case | `L1_AUTO_NOTIFY` | wave re-plan with digital-twin clearance + ≤ reversion budget | **P1** |
| Customer-visible promise change | `L2_HUMAN_APPROVE` | ETA slip past committed window, new commitment to tenant | **P0** |
| Carrier re-booking / re-negotiation | `L2_HUMAN_APPROVE` | re-tender a committed load, rate change | **P0** |
| Committed-capacity re-allocation | `L2_HUMAN_APPROVE` | bump a promised slot to a higher tier | **P1** |
| Cross-tenant arbitrage / 4PL scoring effects | `L2_HUMAN_APPROVE` | re-sequence that shifts another tenant's promise | **P1** |
| Hazardous/regulated incident | `L3_HUMAN_ONLY` | hazmat leak, regulatory hold | **P0** |
| Contract breach / waiver / dispute | `L3_HUMAN_ONLY` | accept liability, waive a clause | **P0** |
| Anything > money cap | `L3_HUMAN_ONLY` | high-cost reroute beyond budget | **P0** |

Rule: **an agent can never exceed the tier policy assigns.** The router still applies the [decision tree](decision-context.md#42-routing-decision-tree) on top of the agent's plan. Level-up of any archetype to a higher tier = policy change = ADR + canary.

## 3. (b) The agent's context window — what it sees before deciding

The context is the [DecisionContext](../schemas/orchestration-events.md#5-decision-context--the-core-object) **plus** a curated window. The Context Assembler builds it; the agent cannot browse further.

| Block | Content | Bound | Why |
|---|---|---|---|
| `facts[]` | read-model snapshot (`asOf`): site capacity, labor, waves/loads, inventory, yard, promises | current state | single consistent view |
| `events[]` (window) | recent event history for the subject: last ≤ 200 events / ≤ 24 h | ring buffer | recency grounding, no unbounded RAG |
| `signals[]` | L4 predictions/recommendations (modelId, conf, featuresHash) | per case | trust is bounded |
| `constraints[]` | SLA, capacity, cost, regulatory, time; `hard=true` inviolable | from policy/MDM | safety frame |
| `memory[]` | prior decisions + outcomes for this subject (last ≤ 20) | bounded | avoid repeating failed plans |
| `prior[plan]` | the plan currently in flight for the case | 1 | diff, don't re-do |
| `runbooks[]` | curated KB excerpts for the archetype | pinned excerpts | no open-infiniti grepping |
| `simulation` | digital-twin outcome for candidate plans | per candidate | cheap blast-radius probe |
| `policyRef` | exact policy version | pinned | audit + reproducibility |

**Explicit exclusions (agents do NOT see):** raw PII beyond minimization, other tenants' data (RLS applies to the assembly), L3 databases, the entire lake, any credential/secret ([nfr.md §5](../nfr.md)).

**Budget guidance (per decision):** context ≤ ~8k effective tokens (default 4k); reasoning ≤ ~2k output; assembly p99 < 3 s; agent step p99 ≤ 5 s total (within the cascade-branch envelope of [nfr.md §2](../nfr.md)). If the window overflows, split the subject's history into a referenced digest — never truncate silently (truncation is a recorded `Fact`).

**Grounding contract:** every fact carries `asOf`; the agent must verify a plan against current `asOf` before proposing (a plan is rejected if base state changed by > drift factor — triggers re-assembly once, else HITL).

## 4. (c) Guardrails & rollback

### 4.1 Guardrail stack (all layers, in order)

| # | Guardrail | Enforcement | Blocks |
|---|---|---|---|
| 1 | **Pinned constitution** (immutable prompt/policy text, versioned) | config + CI hash | prompt injection via inputs |
| 2 | **Pinned model + temperature 0 + structured output schema** | registry pin | nondeterministic drift |
| 3 | **Typed, bounded toolset** — each tool declares read vs command; commands only emit `orch.command.v1` | tool registry | arbitrary side effects |
| 4 | **Context-time OPA** — tiers, allowed options, budgets | policy engine | propose-what-can't-run |
| 5 | **Sim-first** — candidate plan scored in digital twin when available; must beat baseline | L4 sim service | unproven plans |
| 6 | **Execution-time OPA** — policy re-evaluated per command; `commandId` idempotent | Action Executor | TOCTOU between assemble and run |
| 7 | **Budget/impact caps** — max options, max commands, max $/time, max reversions/hour | plan validator | runaway loops/exotic plans |
| 8 | **Shadow → GATE ramp** — new archetype/model starts as `shadow` (log-only); promoted only by human approval with canary % | runtime mode | unproven automation to production |
| 9 | **Kill switch** — per `(decisionType, tenant)` halt new AUTO tasks immediately | runtime | blast-radius containment |

### 4.2 Rollback & compensation

- A plan is **an ordered set of options**; any partially-applied plan is undone by the existing [saga compensation](../adr/0011-orchestration-saga-statechart-engine.md) machinery — compensations are declared per command type.
- **Revert commands** exist for the risky archetypes (e.g., `wms.wave.resequence.v1` has an inverse that re-locks prior sequence): restore from the before-snapshot in the `DecisionRecord`.
- **Before/after snapshot** persisted per plan; a revert restores it.
- **Reversion budget** (e.g., ≤ 3 re-sequences/case/hour): exceeding it forces HITL.
- If a compensating step fails: case → `AWAITING_HUMAN`, escalate ([escalation.md](escalation.md)).

### 4.3 Failure convention

Every guardrail rejection is recorded as a **negative decision** (`DecisionRecord` with `plan: []`, `rationale: "GUARDRAIL: …"`) — rejections are data for policy tuning, not noise.

## 5. (d) Measuring agent performance vs. baseline

Every eligible decision type keeps a **baseline holdout** (human decision path) hot, even after automation — that is the model we must beat.

### 5.1 Offline (pre-deployment)

| Test | Method | Pass criteria |
|---|---|---|
| Replay fidelity | replay last 90 d of cases; compare agent plan to the human's actual decision | agreement ≥ 85% on "must intervene" cases |
| Optimality | simulate agent plan vs. challenger solvers / human plans in digital twin | plan objective ≥ 95% of best-known for auto classes |
| Coverage | % of cases the agent flags that a human also escalated | recall ≥ 95% on "must escalate" set |
| Hazard regression | replay incident/hazmat cases; assert L3 routing | 0 auto steps on L3 archetypes |
| Robustness | seeded prompt/structure weirdness; fuzz inputs | 0 un-gated side effects |

### 5.2 Online (post-deployment)

Randomized **5–20% holdout**: eligible decisions round-robin to baseline path + agent path; objectives identical (policyRef pinned both sides).

| KPI | Definition | Meaning |
|---|---|---|
| SLA-at-risk resolved % | cases where committed SLA held | primary outcome |
| Promise adhere % | % of customer promises kept (auto vs baseline) | external commitment quality |
| Ops cost / shipment | labor + detention + OT per shipment | cost quality |
| MTTR (exception) | case open→auto-resolved | velocity |
| Human override rate | supervisor changes a `L2` agent plan | judgment mismatch |
| Override-away-from-agent | overrides that *then* failed | agent was right |
| Escalation rate (of auto cases) | auto case → human mid-execution | confidence calibration |
| Quality incidents | mispicks, damages, missed appointments attributable to a plan | safety/quality |
| Guardrail rejection % | plans blocked by execution-time OPA | policy fit |
| Sim yield gap | predicted plan outcome vs. actual (tracked per model version) | model honesty |

**Governance:** models/archetypes are champion/challenger in the registry; an archetype that loses to baseline for 2 weeks on primary outcome → auto-demote to `L2`/shadow. Drift (decision distribution, features, rejection mix) alerts per [nfr.md §7.2](../nfr.md).

## 6. (e) Worked trace — warehouse agent recovers an inbound delay

**Scenario & subject:** inbound `INB-9123` → DC `DC-AMS-01`; predicted arrival slips 14:00 → **15:20** (L4 prediction conf 0.86). Receiving was to feed two late-shift pick waves; outbound order `ORD-771` (tier-1) promised 18:00 sits in a dependent wave `W-21`.

**Goal policy:** `MIN_SERVICE | BALANCE_COST_SERVICE`; `BALANCE_COST_SERVICE`; committed promises are hard constraints; internal re-sequencing ≤ reversion budget is `L1`.

| T+ | Step | Tool/event | Detail |
|---|---|---|---|
| 00:00 | Perceive | `vis.milestone.events.v1` → Trigger Evaluator | inbound-slip milestone; rule `LATE_INBOUND` matches (ETA Δ > threshold) → `orch.trigger.v1` `TriggerEvaluated` (`HIGH`, `dedupeKey=LATE_INBOUND:INB-9123`) |
| 00:01 | Assemble | Context Assembler | `DecisionContext #1`: facts = waves `W-17..W-26` (start, dependencies, staff 18 pickers, 3 belts, current ETA-based receiving window), inventory on hand per wave SKU, `ORD-771` promise deadline 18:00, load board; signals = predicted 15:20 @ 0.86; constraints = hard{SLA `ORD-771`, capacity 3 belts, reversion budget 3} |
| 00:02 | Reason (policy + LLM) | Reasoner | Goal = protect `ORD-771` + minimize mispick risk + no labor OT. Candidate plans **P1** re-sequence waves to pull `W-21` after receiving (15:35) and slot `W-20` earlier; **P2** split receiving staff; **P3** move `ORD-771` pick to alternate DC (cost, HITL flag). |
| 00:03 | Sim-first | Digital twin (L4) | Twin runs **P1** vs. current; result: 0 SLA misses, 0 overtime, mispick risk ↓ vs baseline, conf 0.91 → **P1 chosen** |
| 00:04 | Guardrails | OPA + validator | context-time tier=`L1` (internal only, ≤ budget, no promise mutation); sim clearance ✓; reversion count = 1 (≤ 3) ✓; LLM allowed for `DC-AMS-01` archetype=WAVE_REPLAN ✓ |
| 00:05 | Route | Decision Router | Wave re-plan → `AUTO` (\(L1\)); carrier notify → `AUTO` (informational, `L1`); customer ETA → policy check: new worst-case still **meets** committed 18:00 → **within-promise** ⇒ `L1` notify; no re-commitment needed. |
| 00:06 | Execute | Action Executor | 3 idempotent commands (each `commandId` uuid): `wms.wave.resequence.v1` (reorder `W-20`/`W-21`), `tms.carrier.notify.v1` (webhook, HMAC, informational), `vis.eta.update.v1` (within-promise, to customer portal). |
| 00:07 | Observe | events returned | `wms.wave.resequenced.v1`, carrier webhook 200, porcelain acked; milestones reflected |
| 00:08 | Record | Decision/Audit Store | 3× `DecisionMade` + full `DecisionContext` + plan trace (P1, sim margin, guardrail checkmarks, model/policy/prompt versions) |
| 00:15 | Learn/verify | loop guard | actual GPS disagrees > 8% with sim → **one** re-plan allowed (reversion count 2); if more → `AWAITING_HUMAN` |

**Trace as a sequence diagram:** [`diagrams/warehouse-agent-trace.mmd`](diagrams/warehouse-agent-trace.mmd).

**Why this stayed autonomous:** internal-only, fully reversible within budget, sim-validated, no promise/contract mutation, partner comms informational. **What would escalate it:** (i) mismatch of `ORD-771` promise ⇒ carrier or customer re-commitment ⇒ `L2_HUMAN_APPROVE` (SLA 15 m timer), (ii) hazmat loads touched ⇒ `L3_HUMAN_ONLY`, (iii) reversion budget exhausted ⇒ `L2`.

## 7. Multi-agent topology & handoff

| Agent | Scope | Auto ceiling | Handoff to |
|---|---|---|---|
| `warehouseAgent` (per site) | waves, labor ≤ budget, slots, receiving | `L1` | loadPlanner, carrierAgent |
| `yardAgent` | yard slots, appointments, gate | `L1` | carrierAgent |
| `loadPlanner` (network) | load consolidation, trailer mix | `L1` | carrierAgent |
| `carrierAgent` | tracking, informational notify | `L1` | — |
| carrierAgent (re-negotiation) | re-book/re-tender, rates | `L2` | human supervisor |
| `promiseAgent` | ATP, ETA prognosis, within-promise updates | `L1` | commsAgent, human if re-commit |
| `commsAgent` | customer/parent notifications | `L1` | — |

Handoff = a `DecisionRequested` with the **full context attached** (agent's peek is always the same object a human sees). No agent-to-agent raw message passing; workflows route via cases so everything is auditable and idempotent.

## 8. Observability & NFR alignment

- **Tracing:** `traceparent` in event headers spans TriggerEvaluator → Assembler → Reasoner → Executor → ack; the reasoner span carries `modelVersion`, prompt hash, token count, latency, and sim margin ([nfr.md §7.1](../nfr.md)).
- **New alarms** (p99 budgets from [nfr.md §2](../nfr.md)): reasoner timeout > 5 s → SEV2; guardrail-rejection spike > 5 % / 15 min → SEV2; plan drift > 8 % sim gap → SEV3; reversion-budget exhaustion rate > threshold → SEV3; kill switch fired → SEV1.
- **Capacity:** agent runtime autoscales with case load; LLM calls are rate-limited externally and debounced by coalescing ([trigger-catalog.md §4](trigger-catalog.md)).

## 9. Limits (design honesty)

- Agents **cannot**: create/promote policies, mutate data, change autonomy tiers, spend above cap, or act inside a `L3` archetype.
- LLM reasoner is **not required** for execution correctness — every auto path has a deterministic fallback so a model outage degrades to policy+solver, which is still capable of the same plan classes.
- Multi-step *negotiation with external humans* stays `L2+`; agents propose, humans commit.