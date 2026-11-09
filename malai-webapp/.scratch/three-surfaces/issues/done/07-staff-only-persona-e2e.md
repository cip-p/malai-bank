# 07 — Staff-only persona e2e: the ops reviewer's login is a demo beat

Status: ready-for-agent

## Parent

`.scratch/three-surfaces/PRD.md`

## What to build

Prove the role model is real with the seeded ops reviewer — an admin-only identity with deliberately no customer estate. A second Playwright setup project signs her in through the same direct-Cognito path as the shared user and writes a second storage state; her credentials come from documented environment overrides with a secret fallback mirroring the existing end-to-end-test-user pattern (the credential path itself is a backend/infra hand-back). The staff journey spec asserts the login that used to white-screen: she lands on the Ops surface per the Landing precedence, Banking is Withheld politely (the Access page at Banking URLs, no holder resolution attempted, no crash), and her user menu offers only what her roles offer. The suite stays serial — one more shared-pool identity, same GlobalSignOut constraint.

## Acceptance criteria

- [ ] A second setup project produces a staff storage state without touching the shared evaluator's
- [ ] Signing in as the ops reviewer lands on the Worklist, not Banking
- [ ] Visiting Banking URLs as staff renders the Access page — no invariant violation, no white screen, no customer API failures in the API-failure guard
- [ ] Her user menu lists Ops and Exec, not Banking rows; sign out works from the Ops shell
- [ ] Credential resolution is documented (env overrides; secret fallback named), with no credentials committed to the repo
- [ ] The full existing suite still passes serially alongside the new project

## Blocked by

- 01-contract-refresh-surface-scaffold-and-worklist
- External: an e2e credential path for the seeded ops reviewer (backend/infra hand-back — backend `.scratch/three-surfaces` issue 08)
