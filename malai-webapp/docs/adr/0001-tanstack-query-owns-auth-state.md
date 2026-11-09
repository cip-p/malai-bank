# TanStack Query owns auth state

Auth is cookie-based (HttpOnly `access_token` + `refresh_token` set by the backend), so the JS can never read the token directly. We treat the `GET /api/v1/user` query as the single source of truth for "am I signed in?" — a 200 response means signed in, a 401 means signed out, the login mutation invalidates the query on success and the route gates re-render off the new query state. No Zustand auth store mirrors this — duplicating server-issued state into client state would create a drift surface for no gain.

## Considered options

- **TanStack Query as auth source (accepted).** One cache, one source of truth, no manual sync.
- **Zustand auth store mirroring the query.** Familiar React shape; rejected because every login / refresh / signout / 401 has to be replicated into the store, and any forgotten edge case becomes a "logged-in UI on a logged-out cookie" bug.
- **Client-side tokens via `Authorization` header.** Explicitly contraindicated by the OpenAPI (`webapp clients should prefer the cookies over the body tokens`) and re-opens the XSS exposure that HttpOnly cookies were chosen to close.
