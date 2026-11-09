# 03 — The Worklist speaks AML + the CASE correlation link

Status: done

## Parent

`.scratch/aml/PRD.md`

## What to build

The existing MDR screens learn AML. Two touches:

First, the verdict-highlight module extends so an Alert row on the Worklist says what it concluded — its Typology and risk score — keeping the Worklist's glossary promise. Screening rows render exactly as before; rows whose verdicts speak neither vocabulary keep the existing fallback.

Second, the Decision view renders a `CASE` correlation ref as a link to that Case's view — free and exact for SAR decisions, whose correlation ref is the case id. `ACCOUNT` and `PAYMENT` correlation refs stay text: no client-side join is invented to resolve an Alert's case from the record.

Demoable alone: an AML alert on the Worklist reads its typology on the row; a seeded SAR decision's correlation ref navigates to its Case.

## Acceptance criteria

- [x] An Alert row on the Worklist shows its Typology and risk score as its conclusion
- [x] Screening decision rows render exactly as before the change
- [x] A `CASE` correlation ref on the Decision view links to the Case view; `ACCOUNT` and `PAYMENT` refs remain plain text
- [x] Colocated unit tests per house style; lint, prettier, and the unit suite green

## Blocked by

- `02-case-view-assembly.md`

## Done

Landed 2026-07-26.

- `verdictHighlight` now returns `{ conclusion, detail }` — one shape, two vocabularies. Screening keeps precedence (`decision` / `rule`) so those rows render byte-identically; an AML verdict's `typology` reads through `TYPOLOGY_LABEL` (the Case view's own names) with `risk <n>` as the detail, matching the Case view's phrasing.
- An unnameable typology recognises nothing and falls to the raw-JSON fallback rather than printing an enum constant at an evaluator. A new typology therefore fails `TYPOLOGY_LABEL`'s `Record<AmlTypology, string>` at compile time, not silently on screen.
- The `CASE` correlation ref renders as a `Link` to `/ops/cases/:caseId` in place of the id text; `ACCOUNT` and `PAYMENT` stay text, asserted per type.
- CONTEXT.md's **Worklist** entry now records the two-vocabulary promise and the raw fallback.
