# Single-flight refresh middleware

Status: ready-for-human

## Parent

`malai-webapp/.scratch/passwordless-auth/PRD.md` (decision record: backend auth-service ADR-0002)

## What to build

A deep module in the fetch layer: any 401 from a non-auth endpoint triggers exactly one empty-bodied refresh-token call (concurrent 401s coalesce onto one in-flight promise), then one retry of the original request. Refresh failure falls through to the existing unauthenticated transition; auth endpoints are excluded so the middleware can never loop. No proactive timers — reactive single-flight is the whole design. The observable outcome: an expired session renews invisibly mid-use, and a cold page load within the 30-day refresh window restores the session.

## Acceptance criteria

- [x] Vitest with mocked fetch covers: 401 → refresh → retry succeeds; N concurrent 401s → exactly one refresh; refresh failure → unauthenticated path; non-401 responses untouched; auth endpoints exempt; no retry storms (one retry max per request)
- [x] Middleware applies to all generated-client calls without per-call opt-in
- [ ] Manual verification against a local backend: expire the access token, observe a seamless renewed session
- [x] `npm test`, lint, prettier green

## Blocked by

- `malai-backend/.scratch/passwordless-auth/issues/05-refresh-longevity.md`

## What was done (2026-06-10)

Implemented the deep module and wired it; all automatable criteria are green. The one remaining box is the manual local-backend check — it is HITL and depends on backend issue 05 landing the cookie-derived refresh behavior, so it is left for a human.

- `src/api/refreshMiddleware.ts` — `createRefreshingFetch(innerFetch)` returns a `typeof fetch` wrapper. A 401 from any non-auth endpoint triggers exactly one empty-bodied, `credentials: include` POST to `/api/v1/auth/refresh-token`; concurrent 401s coalesce onto one in-flight promise (closure-scoped, reset on settle); on refresh success the original request is retried exactly once; on failure the original 401 is returned and falls through to the existing `useUser` → unauthenticated transition. Requests under `/api/v1/auth/` are exempt so the middleware can never loop. The request is cloned before the first send so POST/PUT bodies survive the retry.
- `src/api/http.ts` — `configureApiClient()` sets `fetch: createRefreshingFetch((request) => fetch(request))` on the shared generated client, so every generated-client call inherits the behavior with no per-call opt-in. The existing `HttpError` error interceptor is unchanged.
- `src/api/refreshMiddleware.test.ts` — 11 Vitest cases over a mocked `innerFetch` covering every acceptance bullet plus body-preserving retry, empty-bodied/credentialed refresh shape, and single-flight reset after settle.

Decision: the refresh body is sent **empty** per the issue/PRD (backend ADR-0002 derives identity from the `refresh_token` cookie), even though the current password-era contract snapshot still marks `RefreshTokenRequest.userId` required. A raw `fetch` (not the typed SDK) is used for the refresh call so the empty body is not blocked by the stale type; the registration/login slices own the contract-snapshot refresh.

Manual verification steps for the human: run the local backend with the issue-05 refresh behavior, sign in, expire/clear the `access_token` cookie (keep `refresh_token`), trigger any protected request, and confirm the session renews without a redirect to `/login`.
