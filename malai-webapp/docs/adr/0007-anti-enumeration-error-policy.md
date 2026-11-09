# Anti-enumeration error policy: generic everywhere, with the one existing carve-out

Every error surfaced to the user across the auth surface — register, confirm-register, forgot-password, confirm-forgot-password, change-password, login — is **generic by default**. The single exception is the existing **wrong-credentials inline message on `/login`** ("Incorrect email or password.") established by ADR-0005.

Crucially, that exception is itself **anti-enumeration-aligned**: it deliberately reads "Incorrect email or password" rather than distinguishing "no such user" from "wrong password". A bad actor probing the login form cannot tell whether they hit a real email or guessed wrong. The principle this ADR encodes is: **never leak whether an email, account, or credential exists in our system**.

Concretely, for this PRD and forward:

- **`POST /auth/register` 4xx** (e.g. UsernameExistsException) → generic toast "Something went wrong. Please try again." No "this email is already registered" inline message — that would let an attacker harvest registered emails.
- **`POST /auth/confirm-register` 4xx** (CodeMismatchException, ExpiredCodeException, NotAuthorizedException) → generic toast. We do not distinguish "wrong code" from "expired code" from "already confirmed". An attacker brute-forcing codes against a known email gets no signal whether the email is even live.
- **`POST /auth/forgot-password` 4xx** → generic toast (the response is already meant to be 200 even for unknown emails, per Cognito convention; we treat any 4xx as opaque).
- **`POST /auth/confirm-forgot-password` 4xx** → generic toast.
- **`POST /auth/change-password` 4xx** (specifically 400 for wrong previous password, per the contract change introduced alongside this PRD) → generic toast. The user is signed in, so enumeration is less of a concern, but we keep the policy uniform.
- **Existing carve-out preserved**: `POST /auth/login` 401 → inline "Incorrect email or password." (covers wrong email and wrong password identically; the message itself is anti-enumeration).

## What changes vs. ADR-0005

ADR-0005's framing was _"generic by default, with one demo-grade exception, expand when we hit a second case."_ The second case has arrived (this PRD's auth surface), and the answer is **not to expand the carve-outs but to constrain them harder, with explicit security rationale.** Inline messages that distinguish failure modes within an auth flow give attackers free signal. We close that surface now, while the relevant code is being written, rather than retrofit anti-enumeration onto whatever ad-hoc copy emerges.

The next inline-message proposal — for any auth or account-existence endpoint — needs its own ADR justifying why the disclosure is safe (e.g. the user is already authenticated and the disclosed fact is about _their own_ state, not a probe of someone else's). The bar to add another carve-out is now meaningfully higher than it was under ADR-0005.

## What this does NOT change

- **Field-level validation via React Hook Form + the generated Zod schema (ADR-0004)** is unchanged. Empty-field, malformed-email, weak-password, code-too-short — all surface inline. Those messages describe the user's own input, not server-side existence facts, so they carry no enumeration signal.
- **The Sonner generic-toast pipeline** (set up by the auth tracer-bullet) is the global error UX. Mutations whose 4xx is consumed by a `setError('root', ...)` inline message (the login carve-out today) intercept before the global handler. Everything else falls through to the toast.

## Considered options

- **Generic-with-one-carve-out, with explicit anti-enumeration rationale (accepted).** Strongest demo posture; cheapest to write; aligned with FCA / Equality Act-grade expectations for a bank demo.
- **Per-flow inline messages (e.g. "Wrong code", "Email already registered").** Friendlier UX in isolation; gives attackers a free oracle on email existence and code validity. Rejected.
- **Translation table from MalaiCode to copy, surfaced inline at every error site.** Most "polished" surface; the most enumeration-leakage. Rejected.
- **Workspace tooling generating the MalaiCode → copy table.** Same problem, more infrastructure.

## Note on the change-password 401 trap

The companion backend change to this PRD splits `POST /auth/change-password` failure into 400 (wrong previous password) and 401 (not authenticated). This ADR is the reason it matters: if 400 and 401 are conflated, the global 401 handler signs the user out on a wrong-previous-password keystroke. The contract change preserves the policy here (generic toast, no inline disclosure) **and** keeps the global session machinery honest.
