# Backend prereq: OpenAPI password policy + change-password 400/401 split + webapp snapshot refresh

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Finishing the auth surface: register, confirm, forgot, change, profile

## What to build

This issue is the coordination gate between `malai-backend` and `malai-webapp` for the rest of this PRD. Two backend changes must land first; this issue's webapp side is to refresh the local OpenAPI snapshot and regenerate the typed client + Zod schemas afterwards.

**Backend changes (land in `malai-backend` first, not in this repo):**

1. **Cognito password policy in the OpenAPI.** Read the Cognito user-pool policy currently configured in `malai-infra` Terraform. Encode it as `pattern` + `minLength` on three schema fields in `malai-backend/malai-api/src/main/resources/static/openapi.yaml`: `RegisterRequest.password`, `ConfirmForgotPasswordRequest.password`, `ChangePasswordRequest.proposedPassword`. Add a comment on each of the three fields that names the matching `malai-infra` resource so future Cognito-policy edits cannot silently drift. Once the backend lands, the OpenAPI is the source of truth for the password policy on those three fields, per ADR-0004.
2. **Change-password contract split.** `POST /api/v1/auth/change-password` currently lists only `401` as its failure response. Split it: return **400** (`ErrorResponse` shape with a `MalaiCode`) when the _previous password_ is wrong; reserve **401** for _not authenticated_ (truly stale token / revoked session). Update the OpenAPI `change-password` response list to `200 / 400 / 401`. The reason matters and is captured in ADR-0007: without the split, the global 401 handler from ADR-0001 signs the user out on a wrong-previous-password keystroke.

**Webapp-side actions (this issue):**

Once both backend changes are merged on `malai-backend`'s main branch, refresh this repo's `./openapi.yaml` snapshot from the backend's source-of-truth file (or via `npm run openapi:gen:remote` for an explicit drift check). Run `npm run openapi:gen` to regenerate `src/api/generated/`. The regen produces:

- A stronger `zRegisterRequest`, `zConfirmForgotPasswordRequest`, and `zChangePasswordRequest` with the Cognito policy encoded — every existing usage already compiles because no current form touches those fields.
- A typed mutation for `change-password` whose error type now reflects the `200 | 400 | 401` shape.

No webapp UI code changes in this issue. Subsequent issues (04, 07, 11) consume the regenerated artefacts.

## Acceptance criteria

- [ ] Backend OpenAPI in `malai-backend` has `RegisterRequest.password`, `ConfirmForgotPasswordRequest.password`, and `ChangePasswordRequest.proposedPassword` annotated with `pattern` + `minLength` matching the live Cognito user-pool policy from `malai-infra`. Each field has a YAML comment naming the matching `malai-infra` resource.
- [ ] Backend OpenAPI `change-password` operation lists `200`, `400`, and `401` as response codes (was: `200`, `401`). The `400` branch uses the existing `ErrorResponse` schema.
- [ ] Backend controller for `change-password` returns `400` for the wrong-previous-password failure mode and `401` only for not-authenticated. Backend tests confirm both branches.
- [ ] This repo's `./openapi.yaml` snapshot is refreshed from the merged backend source.
- [ ] `npm run openapi:gen` succeeds and regenerates `src/api/generated/` with the new schema annotations.
- [ ] `npm run lint`, `npm test`, `npm run prettier`, `npm run build` all pass on this repo after regen.
- [ ] No webapp UI changes are made in this issue — it is the regen step only.

## Blocked by

None — but every subsequent issue that touches password validation (04, 07, 11) is blocked by this one.
