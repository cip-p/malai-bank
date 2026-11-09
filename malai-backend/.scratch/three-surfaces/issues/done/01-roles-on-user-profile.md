# 01 — Roles on the user profile: the frontend learns its hats

Status: done

## Parent

`.scratch/three-surfaces/plan.md`

## What to build

The session cookies are HttpOnly, so the webapp cannot read the JWT's
`cognito:groups` claim — today it has no way to know whether the signed-in
user may be offered the ops view or the manager dashboard. The user profile
read is the natural home for that knowledge: the authenticated principal
already carries the resolved roles.

Contract-first: `UserResponse` in `openapi.yaml` gains a required `roles`
array (enum of the MalaiRole names) before any controller changes. Both
`GET /api/v1/user` and the `PUT` response carry it, populated from the
authenticated principal — never from the database, so the response can never
drift from what the token actually grants.

## Acceptance criteria

- [x] `UserResponse.roles` documented in `openapi.yaml` (required, enum items
  matching MalaiRole names) — additive, oasdiff stays green
- [x] `GET /api/v1/user` returns the principal's roles in stable (enum) order;
  a customer-only token yields `["CUSTOMER"]`, a multi-group token yields all
  of its roles
- [x] `PUT /api/v1/user` responses carry the same field
- [x] A staff-only token (no `customer` group) can read its profile — the GET
  gate widens to every known role, since surface-offering must work for the
  ops reviewer's login too; PUT stays customer-only
- [x] Kensa acceptance tests updated — Kacey `noExtraProps` forces every
  existing user-profile assertion to include `roles`; one test proves the
  multi-group case

## Blocked by

None - can start immediately
