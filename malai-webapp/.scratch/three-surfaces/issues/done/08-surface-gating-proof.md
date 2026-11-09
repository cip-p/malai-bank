# 08 — Surface-gating proof: the customer-only configuration, tested

Status: ready-for-agent

## Parent

`.scratch/three-surfaces/PRD.md`

## What to build

Prove the real-bank configuration with a test, not an assertion in a document. A customer-only persona — a seeded user deliberately excluded from the registration demo grant, since registration now grants every hat and no such user can be created through the app (the seeding is a backend hand-back) — signs in via a third storage state. The gating spec asserts the frontend's withholding and the API's enforcement as separate facts: the Ops and Exec surfaces render the Access page; the user menu offers neither; landing stays Banking; and the admin API refuses the session's token with 403s regardless of anything the UI does — those expected 403s allow-listed in the API-failure guard as the point of the test, not noise.

## Acceptance criteria

- [ ] A customer-only storage state signs in and lands on Banking exactly as any customer
- [ ] Ops and Exec URLs render the Access page, with links back to Banking; neither appears in the user menu
- [ ] The MDR admin operations return 403 for this session, asserted directly in the spec with the statuses allow-listed
- [ ] Banking behaviour for this persona is indistinguishable from today's customer experience
- [ ] The suite stays green serially with all three personas' storage states

## Blocked by

- 01-contract-refresh-surface-scaffold-and-worklist
- 06-exec-shell
- External: a seeded customer-only user (backend hand-back — backend `.scratch/three-surfaces` issue 07)
