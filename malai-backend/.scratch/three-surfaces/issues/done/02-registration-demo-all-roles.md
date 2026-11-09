# 02 — Registration demo policy: evaluators wear every hat

Status: done

## Parent

`.scratch/three-surfaces/plan.md`

## What to build

Registration adds exactly the `customer` Cognito group today, so a
self-registered evaluator can never reach the admin surface. Under the demo
policy, registration also adds `admin` — gated by a configuration property
(default off), so the "a real bank simply doesn't enable this" story stays
honest: role enforcement at the API is untouched; only group membership at
sign-up changes, and only where the property says so.

The property is enabled in the demo deploy unit's configuration and in the
auth-service test context. A failure adding the extra group behaves exactly
like the existing group-add failure path — registration does not half-succeed
silently.

Environment op to schedule alongside (not code): the webapp's shared
Playwright Cognito user predates this change and needs a one-off `admin`
group add per environment, or the webapp's ops e2e journeys will 403.

## Acceptance criteria

- [x] With the demo policy enabled, register issues Cognito
  `AdminAddUserToGroup` for both `customer` and `admin` — asserted at the
  WireMock boundary with group names
- [x] The property defaults to off; the demo deploy unit (`malai-api`)
  enables it explicitly
- [x] A failing `admin` group add fails registration the same way the
  `customer` group-add failure already does
- [x] Existing registration tests stay green

## Blocked by

None - can start immediately
