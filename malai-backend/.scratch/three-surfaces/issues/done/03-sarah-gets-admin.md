# 03 — Sarah gets admin: the scripted persona reaches all surfaces

Status: done

## Parent

`.scratch/three-surfaces/plan.md`

## What to build

The seeded Sarah Mitchell is `customer`-group only, so the scripted demo
persona cannot open the surfaces the self-registered evaluator now gets. The
seeder adds her to the `admin` group alongside `customer`, mirroring the
registration demo policy for the pinned persona.

The ops reviewer is deliberately untouched: it stays `admin`-only, remains
the author of the seeded MDR dispositions, and its staff-only login (no
customer estate) is the demo beat proving the role model is real.

## Acceptance criteria

- [x] `seed` adds Sarah to both `customer` and `admin` Cognito groups —
  asserted via the mocked Cognito boundary in the seeder-module test
- [x] The ops reviewer's seeding is unchanged (`admin` only, no customer
  estate)
- [x] Re-running `seed` stays idempotent (group adds are create-or-ignore on
  the Cognito side)

## Blocked by

None - can start immediately
