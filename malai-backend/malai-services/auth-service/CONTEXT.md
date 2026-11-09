# Auth Service

Cognito-backed user identity for malai. Authentication is passwordless: possession of the email inbox, proven per attempt by a one-time **Confirmation code**, is the only credential.

## Language

**User**:
The authentication identity — an email, a name, and a Cognito record. No password exists anywhere in the system. Canonical definition for all contexts (see `CONTEXT-MAP.md`).
_Avoid_: "account" (collides with the financial **Account**), "customer" (collides with **AccountHolder**).

**Registration**:
The atomic flow that creates a **User**, their default Individual **AccountHolder**, and initial **Accounts** from just a name and email. Leaves the **User** **Unconfirmed**; registering an email that already belongs to a confirmed **User** silently degrades to **Login**.
_Avoid_: "sign-up" in code, "onboarding" (broader KYC-flavoured process, not in scope).

**Login**:
The flow that sends a **Confirmation code** to an email address. For an **Unconfirmed User** it re-sends the registration code instead; for an unknown email it sends nothing while responding identically (see **Indistinguishability**).
_Avoid_: "sign-in" in code, "authentication" (includes the post-login token machinery, which is unchanged by login design).

**Confirmation code**:
The short-lived one-time code Cognito emails as the sole credential, identical in form and email copy whether triggered by **Registration** or **Login**. A fresh one is obtained by re-submitting either flow; there is no separate resend operation.
_Avoid_: "OTP" (in copy), "password", "token" (collides with access/refresh tokens).

**Verification**:
Submitting email + **Confirmation code** to complete whichever flow is in progress — decided by the **User**'s state, not the caller's claimed intent. Success completes a pending **Registration** if there is one, and always establishes a signed-in session. Every failure mode yields one generic error.
_Avoid_: "confirmation" alone (ambiguous with the code), "challenge response" (Cognito mechanics, not domain language).

**Unconfirmed User**:
A **User** created by **Registration** whose **Verification** has never succeeded. Cannot hold a session; reachable from both the register and login entry points, which each re-send their code.
_Avoid_: "pending user", "inactive user".

**Challenge session**:
The server-issued, short-lived state binding a **Login** attempt to the browser that started it, carried as an HttpOnly cookie (body field as non-browser fallback). Issued on every login response — fabricated for unknown emails so its presence reveals nothing.
_Avoid_: "session" alone (collides with the signed-in session), "auth state".

**Indistinguishability**:
The invariant that no response — status, body shape, headers, or error — reveals whether an email is registered. Holds across **Registration**, **Login**, and **Verification**. Response *timing* is a known, accepted exception at demo scale.
_Avoid_: "anti-enumeration" (acceptable in code/docs, too jargony for shared conversation), "email privacy".

**Generated details**:
Profile values invented by the backend at **Registration** rather than collected: a plausible UK address and an adult dateOfBirth. Generation is deterministic under test (injectable, like time and UUIDs).
_Avoid_: "fake data", "defaults".

## Relationships

- A **User** is created by exactly one **Registration** and signs in through any number of **Logins**.
- A **Confirmation code** belongs to exactly one **Registration** or **Login** attempt; **Verification** consumes it.
- A **Login** issues exactly one **Challenge session**; **Verification** of a confirmed **User** requires it.
- **Verification** of an **Unconfirmed User** completes the **Registration** regardless of which entry point sent the code.

## Example dialogue

> **Dev:** A user registers, never enters the code, then a week later tries to *log in* with that email. Dead end?
> **Domain:** No — **Login** sees an **Unconfirmed User** and re-sends the **Confirmation code**. Their **Verification** completes the original **Registration** and signs them in, in one step.
> **Dev:** And if someone registers with an email that's already a confirmed **User**?
> **Domain:** They get a code email like everyone else — it's silently a **Login**, and the name they typed is discarded. **Indistinguishability** means the response can't admit the email was taken.
> **Dev:** What if they enter the code in a different browser than the one that asked for it?
> **Domain:** For a **Login** that fails — the **Challenge session** lives in the original browser. For completing a **Registration** it works; only email + code are needed.

## Flagged ambiguities

- "Confirmation code" historically meant the register-only code, distinct from the forgot-password code. Resolved: passwords and password reset no longer exist; there is one code concept covering both flows.
- The client brief calls verification during login "the same as registration confirmation". Resolved: they are one operation, **Verification**, branching on **User** state — not two operations that look alike.
