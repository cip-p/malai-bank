# Refresh longevity: optional userId, stale-token identity, 30-day access cookie

Status: done

## Parent

`malai-backend/.scratch/passwordless-auth/PRD.md` (decision record: auth-service ADR-0002 — read it first; it explains why the cookie outliving the token is the design)

## What to build

Make refresh work from a cold browser load with nothing but cookies. `userId` becomes optional in the refresh request; when absent, derive the subject from the access-token cookie's JWT via a new stale-token identity reader in the security commons — signature verified against the Cognito JWKS, expiry deliberately ignored. Extend the access-token cookie's Max-Age from the token's expiresIn to the refresh window (30 days) so the stale JWT remains readable. Independent of the passwordless slices — this works against the current login too.

## Acceptance criteria

- [x] Contract marks `userId` optional on the refresh request; supplying it still works (non-browser fallback)
- [x] Acceptance test: refresh with empty body + valid refresh cookie + **expired** access-token cookie succeeds and rotates cookies
- [x] Stale-token reader has isolated tests: expired-but-valid-signature accepted; wrong-signature rejected; malformed rejected
- [x] Refresh with no derivable identity (no userId, no usable access cookie) fails unauthorized
- [x] Access-token cookie Max-Age equals the refresh window; an expired-token request to a protected endpoint still 401s (JWT exp remains the authority)
- [x] Cookie-mode e2e refresh journey updated
- [x] `mvn install` green

## Completion notes (2026-06-10)

- **Stale-token identity reader** (`malai-security/StaleTokenIdentityReader`): decodes the access-token JWT, verifies its RSA signature against the Cognito JWKS via the injected `RSAKeyProvider` (the existing `AwsCognitoRSAKeyProvider` bean), and **ignores expiry** — returns the `sub` as a `UUID`. Any decode/signature/subject failure maps to `UnauthorizedException` (`InvalidJwtTokenException`). Isolated tests cover: expired-but-valid accepted, wrong-signature rejected, malformed rejected, non-UUID subject rejected.
- **Controller** (`AuthController.refreshToken`): when `userId` is absent it derives the subject from `authCookies.readAccessToken(request)` through the reader. Refresh-token resolution (cookie → body) runs first, so a request with a refresh token but no access cookie surfaces `JWT_ACCESS_TOKEN_NOT_FOUND` (5980) → 401.
- **Cookie lifetime** (`AuthCookies.setLoginCookies`): dropped the per-call `accessMaxAgeSeconds` param; the access cookie now takes the refresh-window Max-Age (`refresh-max-age-seconds`, 30d / 2592000) so the stale JWT stays readable. JWT `exp` remains the sole authority — guarded by the existing `JwtDecoderTest` "throw exception when expiresAt is in past".
- **Contract**: `userId` removed from `required` and marked `nullable` on `RefreshTokenRequest`; model field is `UUID? = null`.
- **E2E**: `authServiceReceivesRefreshTokenViaCookie` → `authServiceReceivesRefreshTokenViaCookies` (empty body, both `access_token`+`refresh_token` cookies); `AuthCookieTest` updated. E2E suite is `e2e.skip=true` so it compiles in `mvn install` but runs in the dedicated stage.
- Full `mvn install` green across all modules.

## Blocked by

None - can start immediately
