# PRD — AML on the Ops surface: Cases, the SAR panel, and the ruled close

Status: ready-for-agent

Grilled 2026-07-26; decisions recorded in `CONTEXT.md` (Case list, Case view, SAR panel, Close actions) and ADR-0022. Language follows the webapp glossary and the AML domain language owned by the backend's aml-service context (Alert, Case, Typology, Evidence set, Sweep, SAR draft, Tipping-off). The backend increment this consumes is complete: five ADMIN-only endpoints (case listing, case detail, SAR draft request, close-dismissed, close-reported) plus AML alerts and SAR drafts already flowing onto the MDR Worklist as non-advisory Model Decisions.

## Problem Statement

The backend can now catch patterns — structuring, rapid in-out, high-risk corridors — record each as an Alert on the tamper-evident chain, open a Case per account, draft a grounded SAR narrative, and refuse any close the Gate does not authorise. None of it is visible. An evaluator exploring the webapp today sees AML alerts appear on the Worklist as rows whose verdicts say nothing (the row highlight only speaks the screening vocabulary), with no way to find the investigation they belong to, no case screen, no SAR, and no close. The platform's financial-crime showpiece — "screening stops the £10k payment; AML catches it split into five" — has no stage.

## Solution

The Ops surface gains its investigation area. A third nav item, **Cases**, lists every AML Case newest-first with status chips; the **Case view** assembles one investigation's whole story — the Alerts with their typologies, risk scores, rulings and evidence payments, the **SAR panel**, and the two terminal **Close actions**. Ruling stays where it already lives: each Alert links to its Decision view, one ruling affordance in the UI as the API has one disposition door, and a SAR decision's `CASE` correlation ref links back to its Case. The SAR panel renders a fresh draft inline the moment it is requested, renders a grounding refusal as a recorded fact, and renders the ruled narrative permanently once the Case closes reported. Close refusals are designed inline states mapped from the domain codes — the refusal is the feature, per ADR-0022. The Case list and Case view keep themselves fresh, so a pattern fired live on stage lands on screen within a sweep tick untouched by F5. The Banking surface changes by zero pixels: the tipping-off invariant, on display.

## User Stories

1. As an ops reviewer, I want a Cases nav item on the Ops surface, so that open investigations are findable without querying the MDR by hand.
2. As an ops reviewer, I want the Case list to show every Case newest-opened-first with its status worn as a tag, so that the whole investigation estate is visible on landing.
3. As a bank evaluator exploring alone, I want the seeded closed-reported Case visible the moment I open Cases, so that the full arc tells its story without me reproducing a typology first.
4. As an ops reviewer, I want status chips (Open, Investigating, Closed dismissed, Closed reported) each carrying its depth, so that I can narrow the list to one lifecycle state.
5. As an ops reviewer, I want each Case row to carry the subject account, alert count, and opened/updated recency, so that I can pick the right investigation without opening each one.
6. As a demo presenter, I want the Case list to refresh itself while on screen, so that a structuring pattern fired live lands as a new row within a sweep tick while the audience watches.
7. As an ops reviewer, I want the Case view to assemble the Case's state, its Alerts, their evidence, and the SAR state in one read, so that one screen tells the investigation's whole story.
8. As an ops reviewer, I want each Alert shown with its Typology, risk score, raised-at time, and ruling in force, so that I see what monitoring concluded and what has been ruled at a glance.
9. As an ops reviewer, I want each Alert's evidence payments listed oldest-first with rail, direction, amount, counterparty identifiers, and content hash, so that the pattern is legible and checkable against what the Decider actually saw.
10. As a compliance officer, I want each evidence row's content hash visible and copyable, so that the screen is verifiable against the hash pinned in the Alert's recorded context refs.
11. As an ops reviewer, I want each Alert to link to its Decision view, so that I rule in front of the full record — raw output, refs, rendered prompt, thread — through the one Ruling draft the app has.
12. As an ops reviewer, I want a ruling made on an Alert's Decision view to show on the Case view when I return, so that the two views never disagree about what awaits ruling.
13. As an ops reviewer, I want the Case view to refresh itself while on screen, so that a new Alert attaching mid-investigation appears without a manual reload.
14. As an ops reviewer, I want an Alert row on the Worklist to say its Typology and risk score as its conclusion, so that the Worklist keeps its promise of saying what each decision concluded.
15. As an ops reviewer, I want a SAR decision's `CASE` correlation ref on the Decision view rendered as a link to its Case, so that the record navigates back to the investigation it belongs to.
16. As an ops reviewer, I want a request-SAR-draft affordance on the Case view, so that the writing-up drudgery is drafted for me while the judgment stays mine.
17. As a demo presenter, I want the drafted narrative to render inline the moment the request completes, so that the AI showpiece lands in one press on stage.
18. As an ops reviewer, I want the fresh draft's decision id linked, so that I can open the record, read the cited statements, and rule on the draft through the same door as any decision.
19. As a compliance officer, I want a grounding-refused draft rendered as a designed refusal state — refused, raw output still readable at the record, verdict absent — so that an ungrounded narrative is a recorded fact, never a served one.
20. As a compliance officer, I want a timed-out or errored draft invocation rendered as a recorded fact with its decision link, so that a missing answer is visible on screen the way it is on the chain.
21. As an ops reviewer, I want the SAR panel on revisit to show drafted-status and the ruling in force with a link to read and rule, so that the investigation resumes where it left off without pretending the ephemeral inline draft was the record.
22. As an ops reviewer, I want the attached narrative rendered verbatim and permanently once the Case is closed reported, so that the filed story is readable from the Case forever after.
23. As an ops reviewer, I want close-as-dismissed and close-as-reported offered on every non-terminal Case, so that both terminal doors are visible where the investigation lives.
24. As an ops reviewer, I want each close action to arm on first press and fire on second, so that a terminal transition cannot happen by mis-click and never needs a modal.
25. As an ops reviewer, I want closing as dismissed refused with "an accepted alert's ruling stands" rendered inline as a fact, so that a case with confirmed suspicion visibly cannot be waved away.
26. As an ops reviewer, I want closing as reported refused with "no SAR ruling in force" rendered inline as a fact, so that model output visibly becomes real only through a ruling.
27. As an ops reviewer, I want an already-closed refusal rendered as its own fact, so that a stale view resolves honestly rather than with wrong copy.
28. As a bank evaluator, I want the close buttons never pre-disabled by client prediction, so that I watch the Gate refuse, not the UI prevent — the refusal is the feature.
29. As an ops reviewer, I want the close actions absent (not disabled) on a terminal Case, so that a closed investigation reads as a record, not a form.
30. As a platform maintainer, I want the SAR draft request and both closes to carry idempotency keys that survive retries, so that a flaky network can neither fork the record nor double-close a Case.
31. As a retail customer under investigation, I want my app identical to everyone else's in every pixel, so that the bank never tips me off — no Banking-surface module changes in this increment.
32. As a manager-only user, I want /ops/cases to fall to the Access page like the rest of the Ops surface, so that the investigation area obeys the same offering rules as its siblings.
33. As a customer-only user, I want the Cases screens withheld exactly as the other staff surfaces are, so that surface gating stays one consistent fact.
34. As a bank evaluator, I want the reported arc drivable end-to-end in the browser — pattern fired, case appears, alert accepted, dismiss refused, SAR drafted and accepted, close reported, narrative attached — so that the full loop is something I watched, not something I was told about.
35. As a bank evaluator, I want the refusal arc equally drivable — ungrounded draft refused, close-reported refused, alert rejected, close dismissed — so that the conservative-only posture is provable in the UI, not just the API.
36. As a screen-reader user, I want the new Cases screens to meet the app's accessibility baseline, so that the staff surfaces stay as accessible as the customer ones.
37. As a platform maintainer, I want the API client regenerated from the refreshed contract snapshot before any screen is built, so that every AML read and write is typed end-to-end and a renamed field fails the build, not the demo.

## Implementation Decisions

**Step zero is the contract.** Refresh this repo's OpenAPI snapshot from the backend source of truth and regenerate the client. Every AML surface consumes generated types, queryOptions, and mutationOptions only; contract fields are read typed (the `contentHash` lesson stands).

**Placement.** A third Ops nav item, Cases, between Worklist and Chain, owning the Case list route and the Case view route beneath it. The Ops home stays the Worklist. The glossary rejects "case worklist" — Worklist names the Model Decision list.

**Case list.** One unpaginated listing query; default shows every Case newest-opened-first with a status tag per row; four status chips narrow it, depths derived client-side from the same single fetch (no query-per-chip — the contract's status filter stays unused by the UI). Rows carry shortened case id, subject account, alert count, and opened/updated recency using the existing ops formatters.

**Case view.** Assembled read only: header (case id shortened with full-id copy, subject account, status tag, opened/updated), Alerts oldest-first — typology, risk score, raised-at, ruling-in-force tag, evidence payments with rail, direction, amount, counterparty identifiers, created-at, and content hash (shortened, copyable) — then the SAR panel and the Close actions. Nothing on it is editable and no ruling happens here: each Alert and the SAR decision link to their Decision views, where the Ruling draft already lives. One ruling affordance in the UI, as the API has one disposition door.

**Correlation link.** The Decision view renders a `CASE` correlation ref as a link to its Case view. `ACCOUNT` and `PAYMENT` refs stay text — no client-side join is invented to resolve an Alert's case from the record.

**Worklist rows speak AML.** The verdict-highlight module extends to read `typology` (with risk score) as a row's conclusion alongside the existing screening fields, keeping the Worklist's says-what-it-concluded promise. No other Worklist change; it deliberately does not poll.

**SAR panel state machine.** A pure derivation from (case detail SAR state, fresh draft response, case status) to one of: request affordance; fresh draft rendered inline (ephemeral by design — the case read never carries a draft narrative); grounding refusal / timed-out / errored as recorded facts with the decision id always linked; revisit status with ruling-in-force tag linking to the Decision view; attached narrative verbatim once closed reported. The draft mutation holds a stable per-invocation idempotency key so a retry replays rather than forks.

**Close actions.** Both closes offered on every non-terminal Case, absent on terminal ones, never pre-disabled by client prediction. Two-press inline confirm: first press arms and mints the idempotency key, second fires, clicking elsewhere disarms; the armed key survives retries. A 409 renders as a designed inline refusal state mapped from the domain code — accepted-alert-stands, no-SAR-ruling-in-force, already-closed — unknown codes falling back generic; never a toast. This is ADR-0022: staff surfaces may read domain codes; customer surfaces stay bound by the no-translation rule.

**Freshness.** The Case list and Case view queries poll on a short interval while their page is visible, so live-fired detection lands within a sweep tick. No shared polling abstraction is extracted — a refetch interval on two queries is not yet a pattern.

**Module split** (confirmed): four data hooks (case listing with chip partition inline, case detail, SAR draft mutation, close mutation) following the existing ops hook shape; five pure modules testable without React — the close-refusal mapping, the SAR panel state derivation, the verdict-highlight extension, the case-status tag mapping, and the two-press arm hook; two pages and two components (SAR panel, Close actions); plus the nav item, two routes, and the correlation link.

**Gating and theming are inherited.** The Cases routes live under the Ops surface's existing offering rules and pinned default tokens; nothing new is decided.

**Tipping-off.** No Banking-surface file changes in this increment. The customer app's silence is the point.

## Testing Decisions

A good test exercises external behaviour — the rendered screen, the fired request, the refusal state shown — never the internals that produced them. House style holds: a colocated Vitest file for every module, jsdom + Testing Library for hooks and pages, MSW-free fetch stubbing per the existing ops tests' pattern.

- **Pure modules** — the close-refusal mapping (three codes, unknown fallback), SAR panel state derivation (every state incl. ephemeral-vs-revisit), verdict-highlight extension (typology rows, screening rows unchanged), case-status tags, arm/disarm semantics. Prior art: the existing ops formatter and highlight tests.
- **Hooks** — listing partition + depths, detail polling flag, draft mutation key stability across retry, close mutation key minted at arm. Prior art: the existing ops hook tests and the copilot invocation hook's stable-key test.
- **Pages/components** — Case list chips and tags, empty state; Case view assembly, evidence rendering with copyable hash, decision links; SAR panel per state; Close actions arm/fire/disarm and each refusal fact; nav item and correlation link. Prior art: the Worklist, Decision, and Chain page tests.
- **E2E, default project** (the evaluator holds ADMIN; no screening-mode prerequisite — structuring evidence is sub-threshold by design): the **reported arc** — sandbox top-up, three £3,400 payments to a per-run-unique beneficiary, case appears within a tick (polling proves itself), accept the alert at its Decision view, witness dismiss refused, draft the SAR, read it inline, accept it at its Decision view, close reported, assert the attached narrative; the **refusal arc** — four £3,141.59 payments arm the ungrounded draft: grounding-refusal state, raw output readable at the record, close-reported refused, reject the alert, close dismissed. Both self-heal: terminal closes clear the one-open-case-per-account slate, unique beneficiaries keep evidence sets from colliding across runs, and specs tolerate stale alerts absorbed from a prior failed run. Prior art: the payment-screening and copilot-explanation specs.
- **Gating and a11y** — the Cases routes join the manager-gating and customer-gating assertions and the axe spec's coverage.

## Out of Scope

- Any Banking-surface change (tipping-off is architectural, not copy).
- An embedded ruling affordance on the Case view — ruling stays on the Decision view; revisit only if reviewer friction proves real.
- Worklist polling, and any shared polling abstraction.
- Resolving an Alert's Case from the Decision view (no client-side N+1 join; the contract offers no pointer).
- An account filter or search on the Case list; Case-level severity ordering (flagged in the AML context as unresolved).
- Exec-surface AML metrics (deferred with the exec-metrics backlog).
- A seeded-state e2e dependency — the arcs fire live; seeded Cases are demo furniture, not test fixtures.
- Real filing, SUPPORT_AGENT offerings, and everything the backend PRD already excludes.

## Further Notes

- The sweep runs every ten seconds by default with no environment prerequisite locally — unlike screening's ENFORCE, the live beat needs nothing set.
- Structuring fires at three payments jointly ≥ £10,000, each under threshold; monitoring-decider failures are armed at £1,732.05 (timeout) and £2,236.06 (error) per payment; SAR-decider failures at evidence amounts £3,141.59 (ungrounded), £5,772.15 (timeout), £6,931.47 (error). E2E amounts must dodge the armed values except where a spec arms one deliberately.
- The webapp glossary owns the screen language (Case list, Case view, SAR panel, Close actions); the AML domain language stays owned by the backend aml-service context and is deliberately not restated.
- The backend feature's PRD and exploration live in the backend repo's own scratch tracker under the same slug.
