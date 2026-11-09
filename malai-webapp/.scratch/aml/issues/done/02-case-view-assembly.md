# 02 — Case view assembly

Status: done

## Parent

`.scratch/aml/PRD.md`

## What to build

The assembled investigation read: a Case list row opens the Case view — header (case id shortened with the full id a copy away, subject account, status tag, opened/updated recency), then every linked Alert oldest-first with its Typology, risk score, raised-at, and ruling-in-force tag (or the awaiting state), each Alert's Evidence payments oldest-first with rail, direction, amount, counterparty identifiers (sort code + account number, or IBAN/BIC on SWIFT), created-at, and the content hash shortened and copyable — the screen checkable against what the Decider actually saw. Nothing is editable and no ruling happens here: each Alert links to its Decision view, where the Ruling draft already lives. The view polls on a short interval while visible, so a ruling made on the Decision view shows on return and a new Alert attaching mid-investigation appears unprompted. An unknown case id renders a designed not-found state, consistent with the Decision view's treatment. Contract fields are read typed — never through stringly lookups.

The SAR panel and Close actions arrive in later slices; this view renders their region empty without breaking.

Demoable alone: open the seeded closed-reported Case and read the whole story — alerts, rulings, evidence.

## Acceptance criteria

- [x] The Case view assembles header, Alerts oldest-first, and each Alert's Evidence payments oldest-first as described
- [x] Each Alert links to its Decision view; ruling-in-force tags reflect the contract field, with a designed awaiting state
- [x] Evidence rows show rail, direction, formatted amount, the counterparty identifiers present for their rail, and a shortened copyable content hash
- [x] The view refetches on a short interval while visible; a ruling appended on the Decision view is reflected on return
- [x] An unknown case id renders a designed not-found state
- [x] The a11y spec covers the Case view
- [x] Colocated unit tests per house style; lint, prettier, and the unit suite green

## Blocked by

- `01-contract-refresh-case-list-tracer.md`

## Done

Landed as `src/ops/useAmlCase.ts` (+ test), `src/pages/ops/CasePage.tsx` (+
test), `TYPOLOGY_LABEL` in `src/ops/caseTags.tsx`, the row link on
`src/pages/ops/CasesPage.tsx`, the `/ops/cases/:caseId` route, and two a11y
describes (assembled view light+dark, not-found light+dark).

Decisions worth carrying forward:

- **The row link landed here, as issue 01 promised** — whole-row navigation
  with a real `Link` on the case id, the Worklist row's pattern exactly, so
  keyboard reaches the Case view without a mouse.
- **Not-found follows the Decision view** — `isHttpStatus(error, 404)` split
  from ordinary errors in the hook, a designed `Case not found` heading that
  takes focus, and the back link still offered. The poll additionally stops the
  moment the read errors: a case that is not there is not re-asked every tick.
  Both facts are asserted at the query-options seam, no fake timers.
- **The ruling tag is the Decision view's `RulingTag`, reused** — the contract's
  `rulingInForce` is `HumanDispositionKind`, and its absent case already reads
  "Awaiting ruling". No second vocabulary invented for the same fact.
- **`TYPOLOGY_LABEL` sits in `caseTags.tsx`**, next to the status labels, ready
  for issue 03's verdict-highlight extension to read the same names.
- **Rail and direction labels stay local to the page** — display copy for one
  screen, in the `EMPTY_COPY` / `OUTPUT_ABSENT_FACT` mould, not a module until a
  second caller asks.
- **No SAR or Close placeholder was rendered.** The region is simply absent
  until issues 04 and 05 fill it — a "coming soon" panel would be the one thing
  on the screen that isn't a real bank.

Unrun here: the four new a11y tests. They need a running stack; the assembled
view stubs its case detail through `page.route` (the exec-metrics precedent), so
only the not-found test depends on the API — it expects a 404 for an unknown
case id and allows that failure explicitly.
