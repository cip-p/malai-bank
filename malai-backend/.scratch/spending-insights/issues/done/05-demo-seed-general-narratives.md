# Demo seed: GENERAL narratives

Status: done

## Parent

`.scratch/spending-insights/PRD.md`

## What to build

Give the recategorise feature an obvious home in the demo. Today every seeded narrative maps to a recognised **Category**, so there is nothing crying out to be reclassified and no `GENERAL` slice in the donut. Add a few realistic-but-unrecognised narratives that fall through every **Rule** to `GENERAL`.

Scope of this slice:
- Add 2–3 unmatched narratives (e.g. "Cash withdrawal", "J Patel", "Corner Shop") to the spending-narrative rotation in both seeders (`malai-registration-seeder` and `malai-seeder`), so seeded accounts include entries that categorise as `GENERAL`.
- These must not accidentally match any keyword rule (they should genuinely fall to `GENERAL`).

## Acceptance criteria

- [ ] A freshly seeded account includes at least a couple of transactions that categorise as `GENERAL`.
- [ ] The monthly spending breakdown for a seeded account shows a visible `GENERAL` slice.
- [ ] The recategorise flow (issue 04) has an obvious target: a `GENERAL` seeded item that can be reassigned, after which the `GENERAL` slice shrinks and the chosen category grows.
- [ ] The new narratives do not match any existing keyword rule.
- [ ] Seeder tests updated as needed; `mvn install` green.

## Blocked by

- `.scratch/spending-insights/issues/01-categorised-feed-tracer.md`

## Done

Both seeders share one CURRENT-account transaction path (`malai-seeder`'s
`SeedCommand` delegates to `RegistrationSeederService.seedFor`), so the change
lives in that shared service.

Since slice 03 added the type/sign default tier, an unmatched narrative is only
`GENERAL` when its transaction `type` is neither a rail-payment nor a book-transfer
type — a debit posted with the seeder's existing `type = "TRANSFER"` would fall to
`TRANSFERS` (excluded from the donut), not `GENERAL`. Simply appending narratives to
the rotation would therefore not have produced a `GENERAL` slice. Instead, a fixed,
evenly-spaced set of `GENERAL_SPENDINGS` (Cash withdrawal / Corner Shop / J Patel) is
injected into the spending slots with an unrecognised merchant name and a card/ATM
`type` (`ATM_WITHDRAWAL` / `CARD_PAYMENT`), guaranteeing ≥2 `GENERAL` debits per
seeded account regardless of the random transaction count, spread across the window so
each viewable month has a slice.

- `RegistrationSeederService` posting count / opening-balance model and RNG draw order
  are unchanged, so the deterministic-seed and count invariants still hold.
- `CategoriserTest.seededGeneralSpendingsFallToGeneral` pins the exact
  (merchant, description, type) triples to `GENERAL`.
- `RegistrationSeederServiceIntegrationTest` asserts the seeded account holds the
  `GENERAL` debits with a card/ATM type and a keyword-free merchant.
- Full-reactor `mvn install` green.

The running-stack AC (the donut visibly showing a `GENERAL` slice for a seeded account,
and the recategorise flow shrinking it) is demo-verifiable end to end; every in-repo AC
is covered by the two tests above.
