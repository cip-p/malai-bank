# Behavior-matrix completion: silent degradation and Unconfirmed rescue

Status: done

## Parent

`malai-backend/.scratch/passwordless-auth/PRD.md` (the matrix table in Implementation Decisions is the spec)

## What to build

The remaining matrix cells. `register` with a confirmed User's email silently degrades to a login challenge — submitted name discarded, response indistinguishable from a fresh registration. `register` and `login` with an Unconfirmed User's email both re-send the confirmation code (no new SignUp, no error). Verification stays state-branched: an Unconfirmed User's code completes their original Registration and signs them in regardless of which entry point sent the code. Audit events distinguish the real operation performed, never the response.

## Acceptance criteria

- [ ] Acceptance tests: register with taken (confirmed) email starts a login challenge, ignores the name (full-entity DB assertion proves no mutation), and returns the identical register response + cookie shape
- [ ] Acceptance tests: register and login with an Unconfirmed email both re-send the code; no duplicate Cognito or DB state
- [ ] Acceptance test: Unconfirmed User who initiated from the login entry point completes Registration via verify-code and lands signed in
- [ ] All three entry-point/state combinations remain byte-identical in response shape to their happy-path siblings
- [ ] Audit events record the actual branch taken
- [ ] E2E journeys: unconfirmed rescue via login; taken-email re-register ending signed in to the existing account
- [ ] `mvn install` green

## Blocked by

- `malai-backend/.scratch/passwordless-auth/issues/01-passwordless-registration-end-to-end.md`
- `malai-backend/.scratch/passwordless-auth/issues/02-passwordless-login-end-to-end.md`
