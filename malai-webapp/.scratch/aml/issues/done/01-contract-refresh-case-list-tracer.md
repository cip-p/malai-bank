# 01 — Contract refresh + Case list tracer

Status: ready-for-agent

## Parent

`.scratch/aml/PRD.md`

## What to build

The tracer bullet: the refreshed contract flows end-to-end into a working Case list. Refresh this repo's OpenAPI snapshot from the backend source of truth, regenerate the client, and stand up the Cases area — a third Ops nav item between Worklist and Chain, and the Case list beneath it: every AML Case newest-opened-first, each row carrying its shortened case id, subject account, status worn as a tag, alert count, and opened/updated recency via the existing ops formatters. Four status chips (Open / Investigating / Closed dismissed / Closed reported) narrow the list, their depths derived client-side from the one unfiltered fetch — no query per chip. The list keeps itself fresh on a short interval while visible, so a live-fired pattern lands within a sweep tick. A designed empty state for a bank with no investigations. The status-tag mapping is a pure module in the decision-tags mould.

Demoable alone: against a seeded environment, Cases shows the closed-reported museum piece and the open mid-flight case the moment the nav lands.

## Acceptance criteria

- [x] The OpenAPI snapshot matches the backend source of truth; the client is regenerated and committed; the hermetic verify check passes
- [x] A Cases nav item sits between Worklist and Chain on the Ops surface, active for the Cases routes
- [x] The Case list defaults to every Case, newest opened first, each row with shortened case id, subject account, status tag, alert count, and opened/updated recency
- [x] Four status chips narrow the list, each carrying its depth, all derived from the single unpaginated fetch
- [x] The list refetches on a short interval while its page is visible
- [x] A designed empty state renders when no Cases exist
- [x] The manager-gating and customer-gating specs assert the Cases route falls to the Access page for those personas
- [x] The a11y spec covers the Case list
- [x] Colocated unit tests per house style; lint, prettier, and the unit suite green

## Blocked by

None - can start immediately

## Done — 2026-07-26

Landed as the tracer. Contract snapshot refreshed wholesale from
`malai-backend/malai-api/src/main/resources/static/openapi.yaml` and
regenerated; `openapi:verify` green. `src/ops/useAmlCases.ts` (one unfiltered
listing query, client-side narrowing and chip depths, `refetchInterval` 5s with
`refetchIntervalInBackground: false`), `src/ops/caseTags.tsx` (the pure status
tag in the decision-tags mould, plus `isTerminal` for the Close actions in
issue 05), `src/pages/ops/CasesPage.tsx`, the `Cases` nav item, the
`/ops/cases` route, gating and a11y e2e coverage.

Decisions worth carrying forward:

- **Rows are not links yet.** The Case view is issue 02's; linking a row to a
  route that does not exist would 404 an evaluator mid-demo. Issue 02 owns the
  row link along with the view.
- **Un-narrowing is explicit.** Four status chips exactly, as specified — so
  the way back to every Case is a `Show all cases` button (carrying the estate
  total) shown only while narrowed, with pressing the chip in force a second
  time doing the same thing. The Worklist's always-one-selected chip row was
  deliberately not copied: this list's default is _every_ Case.
- **The poll is asserted at the query-options seam**, not through fake timers —
  `amlCasesQueryOptions()` is the actual contract with TanStack Query, and a
  timer-driven test of a 5s interval buys flakiness for nothing.
- The AML case listing was added to both gating specs' admin-API 403 sweeps,
  since it is ADMIN-only on the same terms as the MDR reads.

Unrun here: the three e2e touches (a11y `/ops/cases` light+dark, narrowed
light+dark; manager-gating and customer-gating `/ops/cases`). They need a
running stack and a seeded environment. The a11y specs assume the Cases listing
resolves for the evaluator — only a live run confirms that.
