# Refresh derives identity from the expired access-token JWT; the access cookie outlives the token

`refresh-token` needs the user's `sub` (Cognito's `SECRET_HASH` for `REFRESH_TOKEN_AUTH` is computed from it), but with HttpOnly cookies the webapp has no identity store to supply it from — on a cold load with an expired token, `GET /user` is exactly the call that fails. So `userId` is optional in `RefreshTokenRequest`, and when absent the backend reads `sub` from the `access_token` cookie's JWT with the **signature verified but expiry ignored**: an expired token is still authentic evidence of *who*, it just no longer authorizes *anything*. To keep that stale JWT reachable, the `access_token` cookie's Max-Age is set to the refresh window (30 days) instead of the token's `expiresIn` — otherwise the browser deletes the cookie at the exact moment refresh needs it. This looks like a bug (a 30-day cookie carrying a 1-hour token); it is the design. Authorization never came from cookie lifetime — `JwtDecoderFilter` validates `exp` on every request regardless.

## Considered options

- **Webapp stores userId in localStorage** *(rejected).* A second source of identity truth that can desync from the cookies (cleared storage, shared machine), reintroducing exactly the state-mirroring problems webapp ADR-0001 removed.
- **Non-HttpOnly userId cookie** *(rejected).* Harmless content, but it breaks the clean "all auth state is HttpOnly" posture for no gain over server-side derivation.
- **Derive from the stale JWT server-side** *(accepted).* The webapp stays fully stateless about identity; the client refresh call is an empty-bodied POST.

## Consequences

- The webapp adds a single-flight 401→refresh→retry fetch middleware (one refresh for N concurrent 401s, one retry, then logged-out as today). No proactive timers — JS can't see HttpOnly expiry anyway. Effective session length = 30 days of inactivity, by configuration.
- The expired-JWT parse must verify signature against the Cognito JWKS — skipping verification would let any well-formed JWT name a `sub`.
- Anyone "fixing" the access cookie's Max-Age back to `expiresIn` breaks cold-load refresh; this ADR is the tripwire.
