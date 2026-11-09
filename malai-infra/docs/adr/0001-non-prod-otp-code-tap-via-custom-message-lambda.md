# Non-prod OTP code tap: the CustomMessage Lambda writes codes to DynamoDB

> **Superseded (2026-06-10) by [ADR-0002](0002-retain-seeded-password-path-for-test-users-no-otp-tap.md).** This mechanism is not buildable: the CustomMessage Lambda only ever receives the `{####}` placeholder, never the plaintext code, so it cannot write the code to any table. Test/Sarah logins instead use a retained seeded password path; the tap is dropped. The record below is preserved as the original (reversed) decision.

With passwordless-only auth (`malai-backend` auth-service ADR-0001), the only place a sign-in code ever exists is the outbound email — Cognito exposes codes through no API. But automated logins still have to happen: the webapp's Playwright suite signs in against the real dev pool, and the seeded demo user (Sarah) has no real inbox at all. So in **non-prod environments only**, the existing CustomMessage Lambda — already in the delivery path of every code — additionally writes `{email → code}` with a short TTL to a small DynamoDB table. Playwright polls the table during `auth.setup`; a human grabs Sarah's code from it the same way. The gate is Terraform environment configuration (the table and the Lambda's write path simply don't exist in prod-shaped deployments), not a runtime flag — a deliberate, visible-by-design security exception with nil blast radius, since malai never has real customers.

## Considered options

- **Real mailbox automation** (SES receipt rules → S3, or a hosted test-inbox service) *(rejected).* Covers Playwright but not Sarah (her address isn't real), adds polling latency and an external dependency to every e2e run, and still needs AWS plumbing comparable to the tap.
- **Keep a password path for test users** *(rejected).* Contradicts passwordless-only — the pool's sign-in policy and app-client flows would have to retain password machinery solely for tests, which is the cruft the migration removes.
- **Code tap via the CustomMessage Lambda** *(accepted).* One mechanism covers registration codes, login codes, Playwright, and Sarah; the prod email path stays byte-identical.

## Consequences

- All webapp e2e auth and any seeded-user login depend on this table existing in dev; it is load-bearing test infrastructure, not a convenience.
- Live demos do **not** use the tap — the presenter receives the real branded email on screen, deliberately.
- A prospect reading the Lambda will find the tap; the non-prod gating must stay obvious in the code so it reads as engineering discipline rather than a hole.
