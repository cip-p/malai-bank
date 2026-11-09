# 09 — Ops demo user: a seeded admin identity for the back-office

Status: done

## What to build

The seeded demo environment has exactly one human: Sarah Mitchell, a `customer`-group Cognito user. Nobody can log into the admin surface — the MDR endpoints, audit chain verification, and the forthcoming ops pages all require the `admin` Cognito group, and no such user exists. The ops demo story ("a reviewer opens the worklist and rules on a Decision") has no one to play the reviewer.

Seed a second demo identity: a back-office ops reviewer, created alongside Sarah with the same idempotent Cognito pattern (create-or-fetch sub, permanent demo password) but added to the `admin` group instead of `customer`, plus the auth `users` row keyed on the Cognito sub so login succeeds. Deliberately no account holder, individual, or accounts — back-office staff are not customers, and the absence is itself demo-accurate. Clean removes the ops user's rows and Cognito identity with the same email-scoped surgical approach used for Sarah, leaving everything else intact.

## Acceptance criteria

- [x] `seed` creates the ops identity: Cognito user in the `admin` group with a permanent demo password, and a confirmed `users` row keyed on the Cognito sub (static fallback UUID when Cognito is disabled, mirroring Sarah's local-dev path)
- [x] The ops user has no account holder, individual, address, or accounts
- [x] `clean` deletes the ops user's `users` row and Cognito identity, scoped by the ops email; Sarah's rows and shared system rows untouched
- [x] Re-running `seed` against an already-seeded environment is a no-op for the ops identity (Cognito create-or-fetch, users upsert-by-id)
- [x] Seeder-module test asserts the seeded `users` row and the Cognito group-assignment call

## Blocked by

None - can start immediately
