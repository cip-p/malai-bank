# Sandbox is a peer of Settings, not a child, and not a top-level tab

The **Sandbox** surface (CONTEXT.md) exposes malai's controllable demo state — the **Counterparties** catalogue and the **Inbound payments** simulator — to the signed-in **User**. Two audiences reach for it: a bank executive narrating a live demo (catalogue lookup + action buttons), and an unguided prospect who registered on `app.fcd8ayxqr72.com` and is exploring without a guide. Both audiences need to _find_ it; the unguided one cannot be helped by anyone if they don't.

The accepted shape: **Sandbox owns a top-level route `/sandbox`** so it is deep-linkable from docs, Playwright tests, and demo guides; **discovery is via the Profile tab on mobile and the desktop user-menu dropdown**, as a peer of **Settings** (not a child). The mobile bottom-tab strip stays at three (ADR-0010 holds). The desktop top nav stays at its current peer set (Accounts / Move money); the user-menu dropdown grows by one row.

## Considered options

- **New bottom tab on mobile + new top-nav item on desktop.** Maximum discoverability. Rejected: breaks the three-tab rhythm ADR-0010 commits to, and reads as "we are showing you a demo product" at the worst altitude — the consumer surface that is supposed to look like a real UK bank. Real banking-platform vendors expose sandbox tiers, but not via a bottom tab on the end-user surface.
- **Inside Profile / user-menu, peer of Settings (accepted).** Profile is already the bucket for "things about my session" (Settings, Theme switcher, Sign out). Sandbox fits the mental model. Owning `/sandbox` as a top-level route keeps URLs clean while discovery stays unobtrusive.
- **Inside Settings, as a sub-section.** Settings = _user preferences_. Sandbox = _the platform's controllable state_. Different concept; nesting them concept-mixes and over-buries Sandbox for the unguided prospect.
- **Floating "Sandbox" tray (Stripe-style "Test mode" pill).** Most flexible, no nav impact. Rejected for v1: no precedent in the codebase, invents new chrome, and the pill itself is the immersion-break that bottom-tab placement would also cause — just smaller.

## Consequences

- The **Profile tab** (mobile) grows from three rows (Settings, Theme, Sign out) to four (Settings, Sandbox, Theme, Sign out). Existing CONTEXT.md description of the Profile tab as the home for "Settings, Sign out, and the Theme switcher" needs to be widened to include Sandbox.
- The **desktop user-menu dropdown** grows by one item. ADR-0010's note that "the user menu — Settings, Sign out, theme switcher" lives in the header dropdown is now stale by one item; do not retro-edit ADR-0010, but acknowledge the drift in CONTEXT.md.
- Sandbox owns `/sandbox/counterparties` and `/sandbox/inbound-payments` as nested routes. URL fan-out from the two tabs.
- If post-demo feedback shows the unguided prospect is missing Sandbox, promoting it from here to a peer-of-Accounts tab is one focused PR — the inverse direction would be much more disruptive.
- An ADR superseding-or-amending this one is the expected path if the IA changes; do not silently promote.
