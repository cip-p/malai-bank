# No error-code translation in v1, with one exception for wrong-password on login

The API's `ErrorResponse.message` is a numeric `MalaiCode` string (e.g. `"5982"`), opaque by contract. Translating it requires either a hand-maintained client-side table or workspace tooling that emits the table from the backend Kotlin enum. We choose neither for v1: every non-RHF error surfaces as a generic _"Something went wrong. Please try again."_ toast. Field-level validation continues to surface inline via React Hook Form + the generated Zod schema (ADR-0004).

The one exception is **401 on the login mutation**, which is hard-coded to render "Incorrect email or password." inline above the login form. This single mapping covers the most visible demo-grade failure mode (fat-fingered password during a demo) without committing to a translation table. Every other error stays generic. When we hit the second case where the demo audience needs better error copy, that's the signal to introduce the table — not before.

## Interaction with the global 401 handler (ADR-0001 / question 7)

Global handler: any 401 invalidates the user query, causing the `<Authenticated>` layout to redirect to `/login`. On the `/login` route itself, that redirect is a no-op (user is already there) and the login mutation's local `onError` still gets to render the special copy. The two paths coexist cleanly without needing opt-out flags on the mutation.

## Considered options

- **Generic + one-case override (accepted).** Lowest cost; preserves the one bit of demo polish that's load-bearing for the tracer-bullet.
- **Hand-maintained translation table mapping codes to copy + field name.** Better polish across the app at the cost of a drift surface; deferred.
- **Generated table from the backend Kotlin enum.** Contract-grade; defers to when there's enough surface to justify the tooling.
- **Fully generic, no exceptions.** Honest minimalism; rejected because the wrong-password case is on the critical demo path.
