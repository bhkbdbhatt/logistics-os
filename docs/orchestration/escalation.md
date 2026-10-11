# Escalation Rules

> Status: Accepted · Related: [README.md](README.md) · [decision-context.md](decision-context.md) · [workflow-engine.md](workflow-engine.md) · [../schemas/orchestration-events.md](../schemas/orchestration-events.md) · [../cross-cutting.md](../cross-cutting.md)

The Escalation Manager guarantees that **every decision ends in an outcome** — automated, human, or partner — even when agents don't respond or automation can't proceed. Escalation is driven by policy, timers, and conditions, and is fully recorded on `orch.escalation.v1`.

## 1. When to escalate

| Condition | Reason code | Typical tier jump |
|---|---|---|
| Human/partner task exceeds SLA timer | `SLA_BREACH` | +1 level |
| Recommendation confidence < floor and no auto path | `LOW_CONFIDENCE` | to HUMAN |
| Cost impact > executive threshold | `COST_THRESHOLD` | to `L4_EXECUTIVE` |
| Regulated / hazmat / safety-critical | `REGULATED` | to `L3_CONTROL_TOWER` (min) |
| Same decision failed/looped `maxAttempts` times | `REPEATED_FAILURE` | +1 level |
| Partner ack timeout | `PARTNER_TIMEOUT` | to `L3_CONTROL_TOWER`, then re-plan |
| Policy forbids automation and no assignee available | `POLICY_FORBIDDEN` | to on-call |
| Manual "escalate" by an operator | `MANUAL` | +1 level |

Escalation always records `DecisionRecord`, `escalationId`, `fromLevel`, `toLevel`, `reason`, `assignee`, and a new `deadline`.

## 2. Escalation ladder

| Level | Owner | Scope | Default SLA to ack |
|---|---|---|---|
| `L1_AUTOMATED` | workflow / auto-agent | routine, within budget | N/A (instant) |
| `L2_SUPERVISOR` | shift supervisor / planner | site/load scope | 15 min |
| `L3_CONTROL_TOWER` | 4PL control tower ops | cross-domain, customer-facing | 30 min |
| `L4_PARTNER_CUSTOMER` | partner rep / account manager | external coordination | 60 min |
| `L5_EXECUTIVE` | duty manager / exec on-call | systemic, high cost/legal | 2 h |

A case escalates **one level at a time**; each level gets a fresh SLA timer. Ladder is configurable per tenant, severity, and decision type.

## 3. SLA timers per autonomy state

| State | Timer | On fire |
|---|---|---|
| `AWAITING_HUMAN` (L2) | 15 min | `EscalationRaised` → `L3_CONTROL_TOWER`, re-notify |
| `AWAITING_HUMAN` (L3) | 30 min | → `L4_PARTNER_CUSTOMER` or `L5_EXECUTIVE` |
| `AWAITING_PARTNER` | per contract (e.g. 60 min) | `PARTNER_TIMEOUT` → `L3`, begin `RECONCILING` |
| `EXECUTING` | 5 min | re-check command acks; if stalled → `RECONCILING` |
| `VERIFYING` | 10 min | if no confirming event → `RECONCILING` |
| `RECONCILING` | policy | options exhausted → `ESCALATED` |

Timers are durable (Temporal) and survive restarts. Each fires at most once per state entry (transition-scoped).

## 4. Severity → routing matrix

| Severity | Initial tier | Notify | Auto-close allowed |
|---|---|---|---|
| LOW | L1 | async | yes if verified |
| MEDIUM | L1/L2 | async + digest | yes if verified |
| HIGH | L2 | real-time + WS | only after human/partner |
| CRITICAL | L3 | page on-call; SMS/call | no |

## 5. Business hours & on-call

- Routing respects **business-hours calendars** per region/tenant; outside hours, tasks route to on-call groups and severity floors may apply.
- **On-call rotation** (PagerDuty-style) resolved by `onCallGroup` from policy; `assignee` filled from the active rota.
- CRITICAL always pages; HIGH pages outside hours only.
- **No-dead-end rule:** if no human ack at the top tier, policy specifies a default: either **proceed with the recommended option** (bounded, reversible) or **hold** the affected work and open an executive incident. Default is per tenant/severity.

## 6. De-escalation & auto-resume

| Situation | Action |
|---|---|
| Human acks within SLA | `EscalationResolved(resolution=ACKED_AND_ACTED)`; return to `EXECUTING` |
| The underlying trigger condition clears (e.g. inbound recovered) | `EscalationResolved(resolution=DEESCALATED)`; case may auto-cancel |
| Higher tier delegates back down | `resolution=REASSIGNED` |
| Automation becomes permitted after context update | `resolution=AUTO_RESUMED` |
| Duplicate/merged trigger superseded by a newer one | case linked; no new escalation |

De-escalation is explicit and audited — never silent.

## 7. Idempotency & audit

- `EscalationRaised`/`EscalationResolved` are keyed by `(escalationId, level, reason)`; replays are ignored if a record exists.
- Every escalation references the `DecisionRecord` it belongs to, enabling cycle-time and MTTA/MTTR metrics per tier.
- Escalations emit on `orch.escalation.v1`; the `control-tower` (L6) and `analytics` (L7) consume them for dashboards and policy tuning.
- Metrics: **MTTA** (mean time to acknowledge), **MTTR** (mean time to resolve), escalation rate per tier, auto-resolution rate, human override rate.

## 8. Anti-patterns

- **Escalating everything** — erodes trust and capacity; thresholds must be tuned per tenant.
- **Silent swallowing** — every timer fire must produce an `EscalationRaised`.
- **Skipping levels arbitrarily** — only policy (cost/regulated) may jump tiers; otherwise one level at a time.
- **No default action at the top** — always define the terminal behavior (proceed vs hold).
