# Enable passwordless sign-in additively + unified code email template

Status: done

## Parent

`malai-infra/.scratch/passwordless-auth/PRD.md`

## What to build

Phase one of the Cognito cutover (per infra ADR-0001 sequencing): the dev pool starts permitting passwordless email-code sign-in while password auth keeps working untouched. Add the sign-in policy allowing EMAIL_OTP as a first auth factor, add USER_AUTH to the app client's explicit auth flows **alongside** the existing password/SRP flows, and merge the signup + resend email templates into one branded "here's your Malai Bank code" template that the CustomMessage Lambda renders for every code-bearing trigger — including whatever trigger source the EMAIL_OTP sign-in flow fires (verify the exact trigger-source string against AWS docs during implementation; same for the sign-in-policy provider syntax). The reset-password template stays for now (it dies in the cleanup slice).

## Acceptance criteria

- [x] Terraform applies cleanly to dev; `ci.sh` (fmt/validate/plan) passes
- [x] EMAIL_OTP is an allowed first auth factor on the pool and USER_AUTH is an enabled app-client flow
- [x] Existing password login against the dev pool still works (additive change, nothing removed)
- [x] A manual `InitiateAuth` with USER_AUTH against dev produces an EMAIL_OTP challenge and a code email rendered from the unified template
- [x] Signup-confirmation and sign-in code emails are visually identical
- [x] Lambda unit tests cover the unified template rendering for each code-bearing trigger source

## Blocked by

None - can start immediately

## Implementation notes (2026-06-10)

- Sign-in policy provider syntax verified against AWS provider v6.43: `sign_in_policy { allowed_first_auth_factors = ["PASSWORD", "EMAIL_OTP"] }`. PASSWORD stays listed until the cleanup slice. EMAIL_OTP first factors are incompatible with `mfa_configuration = "REQUIRED"`; this pool is OPTIONAL so no change needed.
- Trigger source verified **empirically**, not just from docs: a real `InitiateAuth` (USER_AUTH, PREFERRED_CHALLENGE=EMAIL_OTP) against dev fired the CustomMessage Lambda with `triggerSource: "CustomMessage_Authentication"` (observed in CloudWatch). The Lambda renders the unified `templates/code.html` for `CustomMessage_SignUp`, `CustomMessage_ResendCode`, and `CustomMessage_Authentication`; `signup-code.html` and `signup-resend-code.html` are deleted.
- The Lambda role had no logging permissions at all; attached `AWSLambdaBasicExecutionRole` and added a one-line trigger-source log — both needed for the verification above and useful for the code-tap slice (issue 02).
- Lambda test arrangement established at `infra/malai/cognito-malai-api/lambda-tests/` (dependency-free `node:test`, run `npm test` from that dir). Kept outside `lambda/` so the deployable zip stays clean.
- Dev verification transcript: `InitiateAuth` USER_AUTH → `ChallengeName: EMAIL_OTP`, `AvailableChallenges: [PASSWORD_SRP, PASSWORD, EMAIL_OTP]`; `USER_PASSWORD_AUTH` with a wrong password → `NotAuthorizedException: Incorrect username or password` (flow alive, credentials evaluated). Caveat: CLI shorthand `--auth-parameters K=V,K=V` mangles the request — use `--cli-input-json`.
- Cognito rate-limits email OTPs per user; repeated codes were sent to Sarah during verification — if dev throttles her briefly, it self-clears.
