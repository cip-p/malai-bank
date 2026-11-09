# 08 — Decision worklist: list Model Decisions with the Ruling in force

Status: done

## Parent

`.scratch/mdr/PRD.md` (Model Decision Record) — ops-readiness increment

## What to build

An ADMIN-only list read over the MDR: one summary row per Decision, newest first, so a back-office reviewer can see what needs ruling on without already knowing a decision id. Today the read surface starts at `GET .../decisions/{decisionId}` — there is no way to enumerate Decisions, so no worklist page can exist.

Contract-first: the endpoint lands in `openapi.yaml` before any controller. Each summary carries the Decision's identity, Correlation ref, Outcome, model id/version, latency, cost, how many Dispositions have been appended, and the Ruling in force (absent while the Decision awaits its first ruling). An optional filter narrows by ruling state, with a dedicated value for "awaiting ruling" — the reviewer's default view.

No pagination: no list endpoint in the contract paginates, MDR volume is demo-scale, and inventing a paging convention here would be a new pattern this increment doesn't need. Newest-first ordering uses chain seq, which is total and gap-free, rather than `ts`.

## Acceptance criteria

- [x] `GET /api/v1/admin/mdr/decisions` documented in `openapi.yaml` (admin Cognito group, additive change so oasdiff stays green) before the controller exists
- [x] Returns every Decision as a summary row, newest first; a Decision with no Dispositions reports no Ruling in force and a disposition count of zero
- [x] Ruling-state filter: awaiting-ruling returns only unruled Decisions; ACCEPT / EDIT / REJECT return Decisions whose latest Disposition matches; latest-wins semantics proven by a Decision whose ruling changed
- [x] Kensa acceptance happy path: mixed Outcomes and ruling states in one listing, Kacey `noExtraProps` on every row
- [x] Kensa acceptance sad path: non-ADMIN caller refused; unknown filter value rejected as a validation error
- [x] e2e `MdrTest` extended: after the recorded Decision and ACCEPT Disposition, the worklist lists that Decision with Ruling in force ACCEPT

## Blocked by

None - can start immediately
