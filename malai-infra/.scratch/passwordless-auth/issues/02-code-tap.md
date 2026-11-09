# Non-prod OTP code tap

Status: wontfix

## Parent

`malai-infra/.scratch/passwordless-auth/PRD.md` (decision record: infra ADR-0001)

## What to build

The code tap: a small DynamoDB table (email as key, code, TTL of a few minutes) that the CustomMessage Lambda writes to alongside rendering each code email — only in non-prod environments, gated by Terraform configuration so that in prod-shaped deployments the table and the Lambda's write path do not exist at all (no runtime flag). Lambda IAM gains the table write in non-prod only. This is load-bearing test infrastructure: Playwright auth setup and seeded-user (Sarah) logins depend on it.

## Acceptance criteria

- [ ] Triggering any code email in dev (register or login) lands `{email, code}` in the table, readable with dev credentials
- [ ] Entries expire via TTL within minutes
- [ ] The tap's resources and the Lambda write path are absent from prod-shaped configuration — structurally, not behind a flag
- [ ] Lambda unit tests cover: tap write present with non-prod config, entirely absent otherwise, email rendering unaffected in both cases
- [ ] `ci.sh` passes

## Blocked by

- `malai-infra/.scratch/passwordless-auth/issues/01-enable-passwordless-additively.md` (done)
- **A human/architectural decision** — see the implementation finding below.

## Implementation finding (2026-06-10) — BLOCKED: the CustomMessage Lambda never sees the OTP code

Picking this slice up surfaced a flawed premise shared by this issue, ADR-0001, and all three sibling PRDs: **the CustomMessage Lambda is given only the `{####}` placeholder, never the plaintext verification code** — so it cannot write `{email, code}` to the tap. Writing what it *does* have would land the literal string `{####}` in DynamoDB, useless to Playwright.

### Why — verified three ways, no dev deploy needed

1. **AWS docs (Custom message Lambda trigger).** `request.codeParameter` is "a string for you to use as the **placeholder** for the verification code in the custom message." The documented example event shows `"codeParameter": "{####}"`. Cognito replaces the placeholder with the real code **after** the Lambda returns.
2. **The contract enforces it.** `response.emailMessage` "must include the `codeParameter` value ... as the variable `{####}`", otherwise Cognito raises `InvalidLambdaResponseException` and sends no email.
3. **Proof from the live system (decisive).** The current Lambda sets `emailMessage = code.html` with `codeParameter` substituted in and nothing else, and (per issue 01) code emails deliver successfully against dev. That can only succeed if `codeParameter == "{####}"`. Were it the real code, the rendered message would contain no `{####}`, fail the contract above, and no email would arrive. Since emails arrive → `codeParameter` is the placeholder. Confirmed.

The tap *consumers* (webapp Playwright `auth.setup`, demo operators fetching Sarah's code) only read the table, so they are agnostic to how it is written — the blocker is purely on the producer side.

### Path forward — the only Cognito-native option is a re-architecture

The mechanism designed for programmatic access to the code is the **CustomEmailSender** Lambda trigger: Cognito hands the Lambda the code **encrypted** (a KMS key + the AWS Encryption SDK); the Lambda decrypts it → has the plaintext → can both send the email (via SES `SendEmail`) and write the tap. Consumer-compatible, but a large, security-relevant change that contradicts ADR-0001's accepted "via the CustomMessage Lambda" decision:

- Pool `lambda_config` switches from `custom_message` to `custom_email_sender` (+ `kms_key_id` + `lambda_version = "V1_0"`).
- New KMS key for code encryption; Lambda gains `kms:Decrypt`.
- The Lambda must now **send every auth email itself** via SES (subject/body/MIME, all trigger sources) instead of returning a message string — Cognito stops sending mail once a CustomEmailSender is wired.
- Bundle `@aws-crypto/client-node` into the zip (ends the dependency-free-zip arrangement) + SES `SendEmail` IAM.
- Template rendering moves into the new sender Lambda.
- Cross-repo: backend PRD line "template rendering stays in the infra-owned CustomMessage Lambda" must be revised; webapp/backend ADR-0001 references to "the CustomMessage Lambda" too.

Rejected alternatives stay rejected and offer no escape: custom-auth challenge Lambdas (backend ADR-0001) and real-mailbox automation (infra ADR-0001). The backend itself never sees the code under native EMAIL_OTP, so it cannot be the tap producer either.

## Comments

### Resolution (2026-06-10) — wontfix: tap dropped, seeded password path retained for the test user

Maintainer decision: drop the code tap entirely rather than take on the CustomEmailSender re-architecture. The tap only ever existed to let Playwright and the seeded demo user (Sarah) obtain a code; instead, Sarah keeps her email/password account in Cognito (backend seeder unchanged), real users sign in via EMAIL_OTP, and tests/demo log in as Sarah by password. Recorded in [ADR-0002](../../../docs/adr/0002-retain-seeded-password-path-for-test-users-no-otp-tap.md), which supersedes ADR-0001.

Accepted tradeoff: the EMAIL_OTP login journey itself is not automatically tested for now.

No infra change resulted from this slice. EMAIL_OTP already coexists with password on the pool and app client (issue 01), so the desired end-state is already deployed. Knock-on: issue 03 (cleanup — remove password auth, wipe password users) is now `wontfix`, since its whole purpose contradicts retaining Sarah's password.

### What this pass changed

Nothing in `infra/` — building a CustomMessage tap would only ever capture `{####}`. Status moved to `needs-info`: needs a maintainer decision to amend ADR-0001 to CustomEmailSender (recommended; plan it first — it is well beyond a tracer-bullet slice) or to choose another route. If extra certainty is wanted before the pivot, the placeholder can be confirmed live by temporarily logging `codeParameter` from the dev Lambda during an EMAIL_OTP `InitiateAuth`.
