# 09 — Seeded AML demo state and the live-fire script

Status: done
Category: enhancement

## Parent

`.scratch/aml/PRD.md`

## What to build

The demo's two postures. For the self-guided evaluator, the seeder builds a museum with real exhibits: one `CLOSED_REPORTED` case carrying its full arc — alerts, dispositions, the accepted SAR narrative — and one `OPEN` case mid-flight with unruled alerts, all through the real recording paths so chain verification covers them. Seeded personas follow the established seeding conventions (admin-created, permanent credentials).

For the presenter, the live beat: a documented demo script where a seeded persona fires the structuring pattern — five sub-threshold payments to one beneficiary — and the alert lands on the worklist within a sweep tick. No hidden mechanism; the typology rules are the trigger.

## Acceptance criteria

- [x] Seeder run produces the closed case with attached narrative and the open case with unruled alerts
- [x] Seeded MDR and audit entries verify clean under chain verification
- [x] Live-fire script documented; following it produces a worklist alert within one sweep interval
- [x] Re-running the seeder is safe (idempotent demo state)
- [x] `mvn install` green

## Progress

2026-07-26 — landed.

**The museum.** `AmlDemoSeed` defines two monitored customers, both login personas created the
established way (Cognito admin-create, permanent password, `customer` group only — being under
investigation grants no staff surface). Priya Raghunathan carries the finished arc: one
`STRUCTURING` alert over five £2,000 payments, `ACCEPT` ruling, a SAR draft Model Decision citing
the alert and its five payments, `ACCEPT` ruling on that, case `CLOSED_REPORTED` with the ruled
narrative attached. Marcus Delacroix carries the same story mid-flight: a `STRUCTURING` and a
`HIGH_RISK_CORRIDOR` alert, neither ruled, case `OPEN`.

**Recording paths.** Alert and SAR Model Decisions and their Human Dispositions go through
`MdrDecisionService` / `MdrDispositionService`; case transitions through `AuditWriter`. Both
existing chain-verification tests in `SeedCommandIntegrationTest` now cover the museum and stay
green. Only the `aml_cases` row lands by JDBC — aml-service is off the seeder's classpath by
`onlyTheAggregatorDependsOn`, the same reason `BankingDaySeed`'s payments are written directly, and
that table carries no hash chain.

**Derived, not invented.** Alert decision and entry ids and evidence content hashes are computed by
the same formulas the live sweep uses, so a seeded alert is the one the rules would have raised over
the same payments — a re-sweep would dedup against it. Those formulas are *mirrored*, not shared
(the architecture rule forbids the dependency); `AmlDemoSeedTest` pins them with golden values
(mutation-checked) and asserts each seeded alert genuinely satisfies the typology rule and risk-score
band it claims. The coupling is recorded in aml-service's `CONTEXT.md`.

**Live fire.** `docs/aml-live-fire-demo.md`. Fires from Priya, whose case is terminal so a new one
opens; her seeded funding credit leaves the headroom. Detection itself is live-fire proven by the
e2e `AmlTest`, re-run green against the running stack during this slice (23.79s for the whole arc).

## Notes for next iteration

- The museum's evidence payments sit 2–5 hours back: inside one rolling window when made, outside
  the current one now, so they never compete with a live-fired pattern. If the structuring window
  config ever grows past ~3 hours, revisit those offsets.
- The seeded SAR narrative is fixture prose in the template drafter's shape, not a replay of
  `TemplateSarDraftDecider` (which would need audit-event ids and MDR entry timestamps the seeder
  cannot know before it writes them). Its citations are a true subset of the case timeline, so
  grounding holds by construction.
- Not done, deliberately: no automated test drives the documented curl script end to end. It would
  need a Cognito token for a seeded persona against a live stack — the same gap auth-service
  ADR-0001 already accepts for seeded-user sign-in.
