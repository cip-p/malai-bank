# Authentication is passwordless-only, via Cognito's native EMAIL_OTP first factor

> **Update (2026-06-10) — one narrow non-prod exception (`malai-infra` ADR-0002).** The non-prod **code tap** this ADR relied on for test/seeded-user logins proved unbuildable: the CustomMessage Lambda only ever receives the `{####}` placeholder, never the plaintext code, so it cannot write codes anywhere. Rather than re-architect to a CustomEmailSender Lambda, the seeded demo user (**Sarah**) now **keeps a Cognito password**; test infra authenticates her **directly against the Cognito pool** via `USER_PASSWORD_AUTH`, never through the Malai API. The decision below is unchanged for everything that matters here — the **Malai API surface stays passwordless** (`register`/`login`/`verify-code` only; "Keep passwords alongside OTP" stays rejected for the product) — and the exception is confined to non-prod seeded-user test infrastructure. The cost: the EMAIL_OTP sign-in *flow* itself is no longer covered by automated end-to-end tests. See the amended Consequences below.

Malai has no passwords. The sole credential is a short-lived **Confirmation code** emailed per attempt, implemented with Cognito's native passwordless support (`USER_AUTH` flow, `EMAIL_OTP` first factor, passwordless `SignUp`) on the existing Plus-tier pool — no custom-auth Lambda triggers. The API surface is three endpoints: `register` (firstName, lastName, email), `login` (email), and a shared `verify-code` that completes *either* flow and returns the standard token response + cookies. This replaces `USER_PASSWORD_AUTH` login and deletes `confirm-register`, `resend-confirmation-code`, `forgot-password`, `confirm-forgot-password`, and `change-password` — a deliberate, one-time breaking change to `/api/v1` taken while the product has zero external consumers.

## Considered options

- **Keep passwords alongside OTP** *(rejected).* Two parallel auth systems double the surface a prospect's technologist audits, and the password path would read as cruft. Passwordless-only is itself a credible modern-UK-bank story.
- **Custom-auth challenge Lambdas** (`DefineAuthChallenge`/`CreateAuthChallenge`/`VerifyAuthChallenge`) *(rejected).* Full control over code format/expiry/attempts, but three Lambdas plus code-generation state to build and explain. Pre-2024 this was the only way; the pool's tier already includes the native feature.
- **Native EMAIL_OTP** *(accepted).* Declarative infra (sign-in policy + `ALLOW_USER_AUTH` on the app client), and `ConfirmSignUp` returns a `Session` exchangeable for tokens — so a freshly-verified registrant is signed in without a second code.

## Decisions

- **`verify-code` branches on User state, not entry point.** An Unconfirmed User's code completes the registration whether it was requested from the register or login screen; a confirmed User's code completes a login. No stuck states, no "go back to the other page" errors.
- **Indistinguishability is an invariant.** `register` with a taken email silently degrades to login (submitted name discarded); `login` with an unknown email returns the identical 200 and sets a fabricated **Challenge session** cookie; all `verify-code` failures share one generic error; all code emails use one template. The only observable difference is whether an email arrives.
- **The login Challenge session rides an HttpOnly cookie** (body field as non-browser fallback), mirroring the refresh-token dual-mode idiom. Registration verification is stateless (email + code suffice).
- **Address and dateOfBirth are Generated details** — invented at registration by an injectable generator (testable like time/UUIDs), never collected from the user.

## Consequences

- **Response timing is an accepted enumeration channel.** A fresh registration does Cognito SignUp + DB insert + four S2S onboarding calls; the taken-email path does one Cognito call. Fixing this means async registration — out of scope at demo scale, recorded here so it reads as a choice, not an oversight.
- Every *real-user* sign-in requires inbox access. The non-prod code tap originally planned for tests and seeded-user logins proved unbuildable (`malai-infra` ADR-0002 supersedes ADR-0001); instead the seeded user **Sarah retains a Cognito password** and test infra signs her in directly against the pool via `USER_PASSWORD_AUTH` — a non-prod-only affordance that does not touch the API. **Consequence: the EMAIL_OTP sign-in flow has no automated end-to-end coverage** (no programmatic way to obtain a real user's code); verify it manually. Live demos use a real inbox deliberately — the branded code email is part of the pitch.
- The webapp's register wizard loses its address and credentials steps; "Forgotten password?" affordances disappear.
- A code resend is re-submitting `register` or `login`; there is no resend endpoint.
