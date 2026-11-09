# PRD: Passwordless authentication (infra)

Status: partially-superseded (phase one shipped; tap + cleanup dropped)

> **Decision update (2026-06-10) — [ADR-0002](../../docs/adr/0002-retain-seeded-password-path-for-test-users-no-otp-tap.md) supersedes ADR-0001.** Phase one (issue 01) shipped: EMAIL_OTP is enabled *additively* alongside password. The **code tap is dropped** — the CustomMessage Lambda never sees the plaintext code, only `{####}`, so it cannot write it (issue 02, `wontfix`). Instead the seeded test user (Sarah) **keeps her email/password account** as the test/demo login path; real users use EMAIL_OTP. Consequently password auth is **not removed** — the cleanup slice (issue 03) is `wontfix`. The pool is left in its current additive state (`sign_in_policy = [PASSWORD, EMAIL_OTP]`, app client retaining `ALLOW_USER_PASSWORD_AUTH`). Tradeoff accepted: the EMAIL_OTP login journey is not automatically tested for now. The sections below that describe the tap, prod-shaped tap gating, password removal, and the dev-user wipe are historical.

Sibling PRDs: `malai-backend/.scratch/passwordless-auth/PRD.md` (contract + behavior), `malai-webapp-v1/.scratch/passwordless-auth/PRD.md` (UI + Playwright). Governing decisions: this repo's ADR-0002 (retain seeded password path; no tap) which supersedes ADR-0001 (non-prod OTP code tap), backend auth-service ADR-0001 (passwordless via native EMAIL_OTP).

## Problem Statement

The Cognito user pool is configured for password authentication: password-auth flows on the app client, a 12-character password policy doing the work, and three password-flavoured email templates. The backend is moving to passwordless email codes, which the pool does not yet permit — and once no password exists, there is no way for automated tests or seeded-demo-user logins to obtain a code, because Cognito exposes codes only inside outbound email.

## Solution

Enable Cognito's native passwordless sign-in (EMAIL_OTP as a first auth factor) on the existing Plus-tier pool, collapse the email templates into one unified code email, and extend the CustomMessage Lambda so that in non-prod environments it also writes each code to a DynamoDB table — the code tap — which Playwright and humans use to sign in as users with no real inbox.

## User Stories

1. As a backend service, I want the app client to permit the USER_AUTH flow with EMAIL_OTP as an allowed first factor, so that passwordless registration and login work against the pool.
2. As a registering or logging-in user, I want every code email rendered from one branded template regardless of trigger, so that the flows are indistinguishable and on-brand.
3. As a QA engineer, I want each emailed code written to a short-TTL DynamoDB table in non-prod, so that Playwright can complete real passwordless logins.
4. As a demo operator, I want to fetch Sarah's login code from the tap table, so that I can sign in as the seeded user whose inbox does not exist.
5. As a bank evaluator reading the Terraform, I want the code tap to be unmistakably non-prod-gated at the infrastructure level, so that it reads as engineering discipline rather than a backdoor.
6. As a demo presenter, I want prod-shaped environments to deliver codes only by email, so that the live pitch shows the real user experience.
7. As a platform operator, I want the rollout sequenced additively (enable passwordless alongside passwords, then remove password flows after the backend ships), so that the dev environment never has a window where nobody can log in.
8. As a platform operator, I want the dev pool's password-era users wiped once the new flow lands, so that no half-migrated users with vestigial passwords survive for a prospect to find.

## Implementation Decisions

- **Pool changes (Cognito unit)**: add the sign-in policy allowing EMAIL_OTP as a first auth factor; the password policy becomes vestigial but harmless. MFA, advanced-security, groups, and the service (client-credentials) clients are untouched.
- **App client changes, in two phases**: phase one adds USER_AUTH alongside the existing password/SRP flows (additive — backend still on passwords keeps working); phase three (after backend + webapp deploy) removes USER_PASSWORD_AUTH and USER_SRP_AUTH, leaving USER_AUTH + REFRESH_TOKEN_AUTH. Verify the provider syntax for the sign-in policy during implementation.
- **Email templates**: the signup and resend templates merge into one "here's your Malai Bank code" template; the reset-password template is deleted. The CustomMessage Lambda handles whatever new trigger source the EMAIL_OTP sign-in flow fires (verify the exact trigger-source string during implementation) and renders the same template for all code-bearing triggers.
- **Code tap (per ADR-0001)**: a small DynamoDB table (email as key, code, TTL of a few minutes) written by the CustomMessage Lambda alongside rendering — only when the environment is non-prod, gated by Terraform configuration (in prod-shaped deployments the table and the Lambda's write path do not exist, rather than being disabled by a runtime flag). Lambda IAM gains the table write in non-prod only; test consumers read with existing dev credentials.
- **SES, secrets plumbing, and ECS task wiring are unchanged** — client id/secret/issuer flow to the backend exactly as today. Confirm the task role's Cognito IAM action list covers RespondToAuthChallenge (it already appears in the current policy).
- **Dev-pool user wipe** is an operational step after cutover, paired with reseeding (the seeder change is backend-repo scope).

## Testing Decisions

- A good test here pins observable behavior: what the Lambda emits for a given trigger event, and what lands in the table — not Terraform internals.
- **CustomMessage Lambda** gets unit tests (Node) covering: each code-bearing trigger source renders the unified template with the code injected; the tap write happens when the non-prod configuration is present and is entirely absent otherwise; non-code triggers pass through untouched. Follow the repo's existing Lambda test arrangement if one exists; otherwise this establishes it.
- Terraform changes are validated by the repo's existing `ci.sh` (fmt/validate/plan) discipline; the two-phase client-flow change is exercised by applying phase one against dev before the backend lands.

## Out of Scope

- Backend application changes, contract, seeder code (backend PRD); webapp/Playwright consumption of the tap (webapp PRD).
- Custom-auth challenge Lambdas of any kind (explicitly rejected in backend ADR-0001).
- SMS, TOTP enrollment changes, or MFA policy changes.
- Mailbox automation (SES receipt rules → S3) — rejected in ADR-0001.

## Further Notes

- Sequencing contract with the siblings: phase one (additive) here → backend + webapp deploy → phase three (cleanup) here. The cleanup phase is deliberately a separate change set.
- Cognito rate-limits email OTPs per user; rehearsal-then-demo on one account can throttle. Keep a second demo account (also noted in the backend PRD).
