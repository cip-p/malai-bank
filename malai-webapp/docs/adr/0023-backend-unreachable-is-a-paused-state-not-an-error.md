# An unreachable backend is a Paused state, not an error

Prod's backend infrastructure has been torn down. The webapp is still deployed, still loads, still boots React — and then sits on "Loading…" forever, because `useUser` errors with a bare `TypeError: Failed to fetch`, which is neither a 401 (`unauthenticated`) nor a success, so `AuthStatus` stays `unknown` and both `Authenticated` and `Unauthenticated` render their spinner with nothing to resolve it. A bank decision-maker following a link to the demo sees a dead page.

The decision: **when the backend is Unreachable the app declares itself Paused and says so, in the bank's own voice, with a way to reach a human.** Not an error, not a retry loop, not a spinner.

## The probe is the authority, not the failures

Two mechanisms were available.

**Infer it from the failures we already have.** Every API error passes through `queryClient`'s caches. Classify the ones that smell like unreachability and flip a global flag. Costs nothing extra.

**Ask the question directly.** `GET /ready` is public, unauthenticated, already in the contract and already in the generated client. Run it on load and on an interval.

We do both, but only one of them decides. The probe is the single source of truth; a suspicious failure elsewhere only causes the probe to be **re-asked** (`invalidateQueries` on the ready key). The reason is that inference alone answers a question we did not ask: one endpoint 503-ing is not the same fact as the bank being gone, and a flag set from an incidental failure has no way to clear itself honestly. A probe has a definite answer to a definite question, and its recovery is the same mechanism as its detection.

The failure classification (`isBackendUnreachable`) is therefore small and deliberately narrow: a `NetworkError` — the transport never got a reply — or `502`/`503`/`504`, the edge answering with nothing behind it. A `500` is the bank answering badly and stays the calling screen's problem, not the gate's.

Two consequences worth stating:

- **`NetworkError` had to exist first.** The hey-api error interceptor only wrapped failures that carried a response; a transport failure fell through as a raw `TypeError`, indistinguishable from any other thrown thing. `toApiError` now names both cases, and passes an `AbortError` through untouched — a cancelled request is not evidence the backend is gone.
- **The probe carries its own timeout.** A refused connection rejects immediately; a dead host behind a live DNS record can hang instead, and a hanging probe is exactly the stuck spinner we set out to remove. Six seconds, its own `AbortController`, and a hang becomes an answer.

## Takeover, not a banner

The Paused state replaces the entire app — router included. The alternative, an overlay with the app still mounted underneath, preserves in-progress UI state and was rejected: nothing underneath can do anything without the API, background polls keep firing against a host that isn't there, and a half-filled transfer form behind a "we're paused" scrim is a crueller thing to show than a clean page. When the probe recovers, the app remounts and its queries refetch.

The `Toaster` sits **inside** the gate for the same reason. A mutation that fails on the way down still fires the generic error toast; without this it would outlive the app it belonged to and hang over the paused page.

## It says paused, because that is what is true

The copy is not softened failure language. Nothing failed: the environment is switched off, deliberately, and the honest thing to tell a prospective buyer is that it can be switched back on for them. Hence a contact address rather than a status code, and no apology for a fault that did not occur.

- No "error", "outage", "sorry", "unavailable", "try again later".
- No HTTP status, no correlation id, no diagnostic detail — nothing here is for a developer.
- The bank's mark at hero size, because this page is the only thing a first-time visitor may ever see of the product.
- **The brand follows the active theme.** ADR-0011 makes the theme catalogue fictional bank brands; a page that says "Malai's services" under a wordmark reading _halcyon_ breaks the illusion at the exact moment there is nothing else on screen to carry it.

## Recovery is automatic, and offered

The probe polls every 10s while Paused (60s while Reachable), so a backend that comes back takes the app with it, with no reload. The **Check again** button exists anyway: automatic recovery the visitor cannot see or influence reads as a frozen page, and the button is how the page proves it is still trying.

## Enforcement

- `BackendAvailabilityGate.test.tsx` pins the three transitions at the fetch boundary: children render while the first probe is still in flight (a healthy load must never flash the paused page), the app is replaced once the probe fails, and the app comes back on a successful recheck.
- `backendAvailability.test.ts` pins the classification both ways — including that `400`/`401`/`404`/`409`/`500` are **Reachable** — and that a hanging request is given up on rather than awaited forever.
- `queryClient.test.ts` pins that an unreachable-looking failure re-asks the probe, and that the probe's own failure does not re-ask itself into a loop.
- `backend-paused.spec.ts` proves the wiring in the real app by aborting `**/ready`: the paused page takes over the whole router, and the app is handed back when the route is allowed through.
- `a11y.spec.ts` covers the page in both modes, like every other designed state.
