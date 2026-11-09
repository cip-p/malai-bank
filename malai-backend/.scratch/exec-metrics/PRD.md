# PRD — Exec Metrics: the Third Surface Stops Being a Poster

Status: ready-for-agent

Grilled 2026-07-20. Decisions recorded in ADR-0010 (`MANAGER` is a narrow role, separating executive reads from MDR rulings) and ADR-0011 (`exec-service` is a composed read that owns no domain); language in `malai-services/exec-service/CONTEXT.md` (Banking day, Metric, Unavailable, New customers, Deposits, Cards issued, Payments stopped, Metric currency) and `malai-webapp/CONTEXT.md` (MANAGER, Metric tile, Payments stopped).

## Problem Statement

Two of the three Surfaces are real. Banking is a working bank; Ops rules on live Model Decisions that move actual money. The Exec surface is a shell: four tiles rendering em-dashes under a "metrics API pending" caption, honest but inert. A bank evaluator who has just watched a payment get referred, ruled on, and declined then clicks to the executive view — the one built for the person signing the cheque — and finds the one screen in the product that does nothing. The three-surface story ends on its weakest note.

Worse, the number the dashboard most wants to show is specified wrongly everywhere it appears. Both `client-brief.md` and the payment-screening PRD source "fraud prevented" from MDR rulings — one of them from *reject* rulings, which leave a payment parked and prevent nothing. Implementing what the briefs say would produce a wrong number under a claim the platform cannot substantiate.

## Solution

The Exec surface gets real numbers, and every one of them moves in response to something the evaluator did on another Surface. `GET /api/v1/exec/metrics` serves one composed read — new customers, deposits, cards issued, payments stopped — for the current **Banking day**, each metric carrying either a value or an explicit `UNAVAILABLE`, so one sick source degrades one tile to the em-dash the frontend already renders honestly rather than failing the screen. A new `exec-service` owns the composition and the language but no tables, fanning out over the existing client pairs to an internal metric read on each owning context.

The fourth tile becomes **Payments stopped**, read from the `DECLINED` payment state that only an enacted adverse ruling produces — the enforcement loop's own output, not a re-derivation of it. `MANAGER` arrives as a narrow role so a bank can give its CFO the dashboard without giving them the Gate, proven by a seeded manager-only persona that the admin API refuses. And the seeder plants a day's worth of activity that reconciles across Surfaces: the value in the Payments-stopped tile is backed by real referred-then-declined payments whose Model Decisions sit in the Ops worklist, so drilling in rewards the sceptic instead of exposing a seam.

The demo beat: register → New customers ticks up. Simulate an inbound payment from the Sandbox → Deposits rises. Send £10,000, flip to Ops, accept the DECLINE → Payments stopped rises by £10,000. Three Surfaces, one system, visibly.

## User Stories

1. As a bank executive watching a demo, I want the dashboard to show the day's real figures, so that the executive view stops being the one screen in the product that does nothing.
2. As a bank evaluator, I want registering in the demo to move the new-customers figure, so that I can see the surfaces are wired to each other rather than being told they are.
3. As a demo presenter, I want a simulated inbound payment to raise the deposits figure, so that I can move an executive number on cue from the customer app.
4. As a demo presenter, I want declining a referred payment in Ops to raise the payments-stopped figure, so that the enforcement loop I just demonstrated visibly reaches the executive view.
5. As a bank evaluator, I want each figure to state the day it covers and the moment it was taken, so that I am never guessing how fresh a number on screen is.
6. As a bank evaluator, I want a refresh control, so that I can watch a figure change immediately after causing it rather than wondering whether the page is live.
7. As a bank executive, I want one slow or broken source to cost me one number rather than the whole dashboard, so that the screen is never blank at the moment it matters.
8. As a bank executive, I want a number the platform cannot substantiate to be absent rather than estimated, so that nothing on this screen needs a caveat when it appears in a screenshot.
9. As a compliance officer, I want the payments-stopped figure to be the enacted result of human rulings rather than a count of rulings made, so that the number means money that did not move.
10. As an ops reviewer, I want the payments-stopped figure and the Ops worklist to describe the same events, so that drilling from the number into the record reconciles.
11. As a bank technologist, I want the deposits figure to exclude transfers between a customer's own accounts, so that no customer can inflate the bank's headline number from their phone.
12. As a bank technologist, I want figures in different currencies never summed together, so that a multi-currency bank's dashboard cannot silently produce a meaningless total.
13. As a bank decision-maker, I want to grant an executive the dashboard without granting them the power to rule on decisions, so that separation of duties is demonstrable rather than promised.
14. As a bank decision-maker, I want the narrow executive role to be provably refused by the back-office API, so that the role boundary is a test rather than a claim in a document.
15. As a demo presenter, I want one login to reach all three Surfaces, so that the scripted demo needs no re-authentication between beats.
16. As a demo presenter, I want a freshly reset demo environment to show a plausible bank's day rather than zeroes, so that the dashboard is impressive before I touch anything.
17. As a real-bank deployer, I want the demo's all-roles registration policy to stay a property I never enable, so that adopting the platform does not hand every registered user an executive dashboard.
18. As a platform maintainer, I want each number owned by the context that owns its facts, so that adding a fifth metric is a new internal read rather than a new cross-boundary query.
19. As a platform maintainer, I want the banking day defined once, so that the next day-scoped figure inherits a convention instead of inventing a second one.
20. As a bank executive, I want the dashboard to render in bounded time regardless of downstream health, so that a demo never stalls on a spinner.

## Implementation Decisions

- **Composition (ADR-0011)**: one `GET /api/v1/exec/metrics` served by a new `exec-service` module, fanning out over the existing client pairs (`malai-account-holder-client`, `malai-ledger-client`/`malai-payments-client`, `malai-card-client`) to a service-JWT-scoped internal metric read on each owning context. No new client pairs are needed — every pair already exists. `exec-service` owns no tables. Rejected: four screen-shaped endpoints scattered across four domains' contracts; a single service defining JPA entities over other contexts' tables (forbidden in spirit by `noCrossModuleInternalAccess`, and by V5's "the service boundary is HTTP, not the database").
- **Availability**: every metric is `AVAILABLE` with a value or `UNAVAILABLE` with none — a first-class contractual state, not a null. No reason code: the client can act on none, and naming the sick service leaks internal topology. Each fan-out leg carries a per-source timeout; a slow source degrades to `UNAVAILABLE` rather than hanging the screen. All reads, so the loopback-audit deadlock documented in `payments-service/CONTEXT.md` does not apply.
- **Window**: the **Banking day** — since midnight `Europe/London`. This is the platform's first stated timezone convention; `StubbedTimeService` makes it testable. `bankingDay` and `asOf` both appear in the response.
- **Metric definitions**: *New customers* — `account_holders` created in the Banking day, all four types (holders not Users, so a staff-only login never inflates the bank's customer count). *Deposits* — inbound payments that settled in the Banking day, excluding book transfers, card refunds, and anything unsettled; a flow figure, so a later Return does not retroactively reduce it. *Cards issued* — issuance events in the Banking day, not surviving cards. *Payments stopped* — sum over payments in `DECLINED`, the state only an enacted adverse ruling produces.
- **Money metrics carry an explicit currency and report GBP only in v1**; non-GBP payments are excluded and stated as excluded, never silently summed. Minor units of different currencies are not addable.
- **The fourth metric is sourced from `payments`, not the MDR.** Re-deriving it from Human Dispositions would reimplement the accept/edit/reject → actionable-output translation that ADR-0009 keeps in exactly one place, and would miss an *edit*-to-DECLINE. The link to the audit plane survives via `screening_decision_id` on the payment row.
- **Naming**: "fraud prevented" is retired from the vocabulary. The decider is `malai-screening-rules` version 1 — a threshold, a watchlist, one armed timeout — and a declined payment is money the bank stopped, not proven fraud. Both briefs get corrected rather than implemented as written.
- **Role (ADR-0010)**: `MalaiRole` gains `MANAGER("manager","ROLE_MANAGER")`; the metrics endpoint gates on `hasAnyRole('MANAGER','ADMIN')`. Terraform gains a `manager` Cognito group (precedence renumbering is cosmetic — roles resolve from `cognito:groups`, never `cognito:preferred_role`). `RegistrationOrchestrator`'s `demoAllRoles` branch grants it alongside `customer` and `admin`.
- **Contract**: adding `MANAGER` widens the `roles` response enum on `/api/v1/user` — the same conscious approval the payment-state widening needed. The compatibility check will flag it; it is approved deliberately, not worked around. Internal metric reads stay out of the public contract per the documented convention.
- **Seeded baseline**: the seeder plants a plausible Banking day, and it must reconcile across Surfaces — a few payments referred with real `screening_decision_id` pointers, their `MODEL_DECISION` entries recorded under `malai-screening-rules` v1 with `PAYMENT` correlation refs, adverse Human Dispositions authored by the seeded ops reviewer, and the resulting payments in `DECLINED`. The existing `MdrDemoSeed` entries (correlated to `CARD_AUTHORISATION` and `ACCOUNT`) stay as they are; a mixed-model worklist was already accepted as realistic. Seeded timestamps are already now-relative and `ResetCommand`/`demo-reset.sh` re-anchor the estate to run time, so the operational rule is unchanged: reset before demoing.
- **Manager-only persona**: mirrors `CustomerOnlySeed` exactly — pinned UUIDs continuing the scheme, one Cognito group, structurally excluded from the demo grant, no customer estate. It inherits the 403/`4999` degrade path shipped by three-surfaces issue 06, so no new contract work for customer-scoped endpoints.
- **Webapp**: `surfaceOffering.ts`'s `GATES` map changes shape from surface → one Role to surface → set of Roles, because Exec now has two. Tiles bind to the generated hook without layout rework (they were shaped for this). `asOf` renders on the dashboard; an explicit refresh control sits alongside it. This one query opts into `refetchOnWindowFocus` rather than flipping the app-wide `false`. Mount-refetch already works — navigating to `/exec` remounts and refetches with the existing `staleTime` of 0.
- **No polling.** A `refetchInterval` makes numbers drift mid-sentence during a demo, adds Playwright flakiness, and raises a live-region a11y question. Named as a one-line follow-up if a wallboard is ever wanted.

## Testing Decisions

- A good test asserts external behaviour — response payloads, database rows as full entities, what the downstreams were asked, what renders — never aggregation internals.
- **`exec-service` acceptance (Kensa, happy/sad)**: all four metrics available; each source individually unavailable → that metric `UNAVAILABLE` and the other three still valued; a source timing out → `UNAVAILABLE` within the bounded time; `bankingDay`/`asOf` correct against `StubbedTimeService`, including a payment settled just before and just after the `Europe/London` midnight boundary; `hasAnyRole('MANAGER','ADMIN')` enforced — manager token 200, admin token 200, customer-only token 403, no token 401. WireMock lanes per downstream. Prior art: the MDR gate tests and the existing multi-downstream acceptance classes.
- **Per-context internal metric reads**: one acceptance test each, in the owning service. Deposits gets the boundary cases that matter — a book transfer contributes nothing, a card refund contributes nothing, an unsettled inbound contributes nothing, a non-GBP inbound is excluded. Payments stopped counts `DECLINED` only. New customers counts holders and ignores a staff-only user. Cards issued counts a card cancelled the same day.
- **Client pair**: WireMock-backed request-construction and deserialisation tests per the client-module convention, for whichever existing clients gain methods.
- **Role**: a test pins the manager-only seeded user's group membership as exactly `manager`, mirroring the customer-only test. `MalaiRole` mapping covered by the existing security tests.
- **Repository tests**: every new custom finder, per the house rule.
- **Webapp (Vitest)**: `surfaceOffering` over every role combination including `MANAGER`-only and `MANAGER`+`ADMIN`; the metrics hook mapping `AVAILABLE`/`UNAVAILABLE` into tile states; the dashboard page rendering values, em-dashes for unavailable metrics, `asOf`, and the refresh control. Prior art: the existing page tests with in-memory routing.
- **Webapp (Playwright)**: the manager-only gating journey — lands on `/exec`, Banking and Ops render the Access page, the admin MDR API refuses (the 403 being the proof, with the API-failure guard fixture allow-listing it). The a11y spec covers the populated dashboard in both modes.
- **e2e**: one journey that closes the loop across Surfaces — a payment is referred, ruled adversely in Ops, and the payments-stopped figure rises by that amount. Prior art: the payment-screening e2e.

## Out of Scope

- Ops-surface liveness (the worklist has the same staleness gap; it belongs to a separate small Ops slice, not smuggled in here).
- Payment context on the Ops Decision view — the reviewer still cannot see the payment's live state while ruling. Named, separate.
- Polling / wallboard mode for the dashboard.
- Any fifth metric, charts, trends, or comparison-to-yesterday. No charts for the sake of charts.
- Multi-currency aggregation and FX-normalised totals.
- A `SUPPORT_AGENT` surface — still explicitly none.
- Historical metrics, date-range selection, or any window other than the current Banking day.
- Changes to the existing `MdrDemoSeed` entries.

## Further Notes

**Hand-offs:** `malai-infra` adds the `manager` Cognito group and renumbers precedence, and the manager-only persona needs the same human-applied credential secret still outstanding for the seeded ops reviewer (three-surfaces issue 07). The demo script gains the three tile-moving beats once this lands.

**Corrections to existing documents:** `client-brief.md` sources "fraud prevented" from "MDR reject rulings" — wrong twice over, and now retired in favour of **Payments stopped**; the brief's `/manager-dashboard` is dead vocabulary, the Surface is `/exec`. The payment-screening PRD's "falls out of accept-DECLINE rulings" is right in spirit but incomplete (an *edit* to DECLINE also declines) and is superseded by sourcing from the `DECLINED` state directly.

**Slicing note for the issue pass:** the tracer bullet is one metric end to end — contract, `exec-service`, one internal read, the aggregator with availability, and the tile bound — shippable before the other three metrics, the role, or the seeder work begin. `MANAGER` and its persona stack on top without reopening it (the endpoint can gate on `ADMIN` alone until the role lands, then widen). The seeded screening story is the largest single item and is independent of the metrics code — it can proceed in parallel, and until it lands the payments-stopped tile simply reads zero on a fresh reset.
