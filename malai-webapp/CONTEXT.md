# Malai Webapp

The malai web app: three sibling **Surfaces** — customer banking, the MDR back office, and executive numbers — behind one registered login. This glossary covers the domain language the UI must speak; cross-repo language lives in the workspace `CONTEXT-MAP.md` (forthcoming); the MDR domain language is owned by `malai-backend/malai-services/mdr-service/CONTEXT.md` and is not restated here.

## Language

### Surfaces and Roles

**Surface**:
One of the three sibling areas of the app, each with its own chrome and root: the **Banking surface** (`/`), the **Ops surface** (`/ops`), the **Exec surface** (`/exec`).
_Avoid_: "app" per surface (it is one app), "portal", "module".

**Role**:
A hat the signed-in **User** holds, read from the `roles` field of `/api/v1/user` (resolved from the JWT's Cognito groups): `CUSTOMER`, `SUPPORT_AGENT`, `MANAGER`, or `ADMIN`. In the frontend a Role only ever decides which **Surfaces** are **Offered** — enforcement lives at the API.
_Avoid_: "permission" (implies frontend enforcement), "group" (the Cognito mechanism, not the UI concept).

**MANAGER**:
The narrow executive **Role**: it Offers the **Exec surface** and nothing else. It exists so the power to read the bank's numbers is separable from the power to rule on decisions that move money — a bank can give its CFO the dashboard without giving them the **Gate**. `ADMIN` remains the superset and keeps Exec too.
_Avoid_: "executive"/"exec" as a role name (Exec names the **Surface**, not the hat), "manager-dashboard" (retired: the Surface is `/exec`).

**Offered** / **Withheld** (surface):
A **Surface** is Offered when the User's **Roles** include its gate (`CUSTOMER` → Banking; `ADMIN` → Ops and Exec; `MANAGER` → Exec; `SUPPORT_AGENT` → none yet, stated). A Withheld Surface renders the **Access page** — never the NotFound page, never a silent redirect.
_Avoid_: "allowed"/"denied" (security words — offering is presentation), "hidden" (the Access page is visible and honest).

**Landing**:
The Surface a User arrives on after login: the first Offered Surface in precedence `/` → `/ops` → `/exec`; a User with no Offered Surface lands on the **Access page**.
_Avoid_: "home" (each Surface has its own home).

**Access page**:
The calm page a Withheld Surface renders — "this area isn't part of your access" — linking to the User's Offered Surfaces.
_Avoid_: "403 page" / "forbidden page" (HTTP words; the page is a designed state, not an error), "not found" (different fact).

**Reachable** / **Unreachable** (backend):
Whether the API answers at all, as distinct from whether it answers well. Unreachable means the request never got a reply — the transport failed — or the reply came from the edge with nothing behind it (`502`, `503`, `504`). A `401`, a `404`, a `409`, even a `500` all mean **Reachable**: the bank answered, and the app handles that answer where it happens. Decided by the **Readiness probe**, never by guessing from one screen's failure.
_Avoid_: "offline" (that is the browser's connection, a different fact the app does not currently claim), "down" (unqualified — say which).

**Readiness probe**:
The app's own standing question to the backend — `GET /ready`, public, unauthenticated, on a timeout — and the single authority on whether the backend is **Reachable**. Runs on load, polls in the background, and is re-asked immediately whenever any other call fails in a way that looks Unreachable. It is what ends the **Paused state**, without a reload.
_Avoid_: "health check" (that names the backend's own endpoint, not the app's use of it), "heartbeat" (implies the app is being watched, not watching).

**Paused state**:
What the whole app becomes while the backend is **Unreachable**: every Surface is replaced by the **Service paused page**, which carries the bank's mark, says the application is paused, and offers the contact address. It is a **state of the product, not an error screen** — nothing failed from the visitor's point of view, the bank is simply not open. It ends by itself when the **Readiness probe** succeeds.
_Avoid_: "error page", "outage page", "maintenance mode" (nobody is doing maintenance — the environment is switched off), "offline mode" (there is no offline mode; the app does nothing without the API).

**Service paused page**:
The page the **Paused state** renders. Names the active theme's brand, not always malai (see ADR-0011), and its address is the only route out of the app that does not need the API.

### Banking surface

**User**:
The Cognito-backed authentication identity — email, name, and possession of the email inbox (proven per sign-in by a **Confirmation code**; there is no password). Owns nothing financial directly; everything financial hangs off the User's reachable **AccountHolders**.
_Avoid_: "account" (collides with **Account**), "customer" (collides with **AccountHolder**).

**AccountHolder**:
The customer profile that owns **Accounts**. Comes in four variants: **Individual**, **Joint**, **Business**, **Sole-Trader**. A single **User** can reach multiple **AccountHolders** (e.g. their personal Individual plus a Joint with a partner).
_Avoid_: "customer", "profile" (overloaded — see `/api/v1/user` profile), "holder" on its own.

**Account**:
The financial entity that holds money — has a balance, sort-code / account-number identifiers, and a transaction history. Created by the backend during register and surfaced via `/api/v1/accounts`.
_Avoid_: "bank account" (redundant), "wallet".

**Available balance**:
An **Account**'s spendable money — the ledger balance minus everything currently on **Hold**. This is _the_ balance in this app: every figure the customer sees is the available one, per ADR-0021. The **Ledger balance** (`amountMinorUnits`) is the book of record, moves only on settlement, and is never rendered.
_Avoid_: "balance" unqualified when the distinction matters, "current balance" (UK banking uses it for the ledger figure — the opposite of what we show), "cleared funds".

**Hold**:
Money reserved on an **Account** by an in-flight outbound **Payment** — committed but not yet gone, so it has left the **Available balance** while the **Ledger balance** still counts it. Named to the customer only as a total ("£1,000.00 on hold") on the account detail screen, never per-payment. Every in-flight payment places one, screened or not, so the figure carries no signal about **Screening**.
_Avoid_: "pending" (reserved for **Pending card rows** and the forecast glossary), "reserved"/"frozen"/"blocked" (the last two collide with **Freeze** and with **Referral**'s avoid-list), "uncleared".

**Transaction**:
A single line on an **Account**'s history — what a customer sees on a bank statement. Carries a signed minor-unit amount (negative = debit, positive = credit), a running balance at that point in time, a timestamp, and optionally a description, counterparty, merchant, or category. In API terms this maps to one ledger entry (`TransactionItemResponse`) — _not_ to the API's underlying `transactionId`, which groups the two sides of an internal money movement (e.g. a transfer's source and destination entries). The UI only ever renders one side, so we never deduplicate by `transactionId`.
_Avoid_: "entry", "ledger entry" (accounting jargon — leak from the API); "payment" (overloaded with Direct Debits and card payments — distinct concepts).

**Counterparty**:
The other side of a payment — any (sort-code, account-number, name) triple the bank could move money to or receive money from, whether or not the **AccountHolder** has saved it. Lives in the **bank directory** owned by `account-service` (canonically defined in the workspace `CONTEXT-MAP.md`). A **Payee** is a _saved_ Counterparty; a one-off **Transfer** destination is a _transient_ Counterparty. In v1 the malai webapp surfaces Counterparties explicitly only inside the **Sandbox** — every other surface deals with them implicitly via Payees, Transfers, or **Transactions**.
_Avoid_: "beneficiary" (too formal / pensions-flavoured), "third party" (vague), "external account" (collides with **Account**).

**Payee**:
A saved **Counterparty** on an **AccountHolder** — a name + sort-code + account-number combination the holder has chosen to keep for repeated use. Holder-scoped (lives under `MalAccountHolderIdHeader`), not tied to a specific **Account**. CRUD via `/api/v1/payees`. Backed by **Confirmation of Payee** for the name-vs-record check on create.
_Avoid_: "beneficiary" (too formal / pensions-flavoured), "contact" (Monzo-only, doesn't read as UK retail banking).

**Transfer**:
A one-off push of money from one of the **Active AccountHolder**'s **Accounts** to a sort-code + account-number destination. Modelled in the API as `POST /api/v1/accounts/{accountId}/transfers` with `TransferRequest`. In code and types the term is **Transfer**; the user-facing CTA is **"Send money"** — verb-led label matches UK retail-banking convention. Splitting code-term from UI-copy mirrors the existing **Registration** / "Sign up" split.
_Avoid_: "payment" (overloaded — see **Transaction**), "wire" (US-only).

**Direct Debit**:
A creditor-initiated, recurring **pull** of money from one of the holder's **Accounts**. Amount may be variable (`amountMinorUnits` is nullable — utility-style DDs are set at collection time). Surfaced in v1 as a **read-only list**; creating / amending / cancelling requires backend contract changes first and is out of scope for this slice.
_Avoid_: "DD" in user-facing copy (acceptable in code names), "subscription" (overloaded with SaaS), grouping with **Standing Order** under one screen.

**Standing Order**:
A debtor-initiated, recurring **push** of money from one of the holder's **Accounts** to a fixed sort-code + account-number on a fixed schedule. Amount is fixed (non-nullable). Optionally linked to a saved **Payee** via `payeeId`. Surfaced in v1 as a **read-only list**.
_Avoid_: "SO" in user-facing copy, lumping with **Direct Debit** as "scheduled payments" — they are distinct UK retail products and the UI keeps them on separate screens.

**Balance forecast**:
A per-**Account** projection of where the account's _spendable_ balance (its **available balance**) lands by the end of the current calendar month, given the account's active recurring payments — its **Standing Order**s and **Direct Debit**s — still to leave. Outgoings only: income never appears. Surfaced as a card on the account detail screen (headline number + "for 31 July") linking to a dedicated **Balance forecast** screen. The projection can go negative even though a `NO_OVERDRAFT` **Account**'s real balance cannot — that negative is the month-end **warning**, derived purely from the sign. malai models no arranged overdraft, so there is no overdraft line, limit, or headroom framing (the HSBC reference has one; malai deliberately does not).
_Avoid_: "balance after bills" (HSBC's name for it), "budget" (implies planning/limits malai doesn't offer), "prediction" / "projection" in user-facing copy (say "forecast"), "arranged overdraft" (malai has none).

**Completed** / **Expected** (forecast payments):
The two groups a **Balance forecast** splits its recurring-payment occurrences into, at _today_. A **Completed** payment is dated on or before today — already reflected in the **available balance**, shown for context, and never subtracted again. An **Expected** payment is dated after today through month-end — these are what move the forecast (`forecastBalance = availableBalance + Σ Expected`). Each row shows a date, counterparty name, signed amount, and a **Standing Order** / **Direct Debit** tag, and links back to the underlying instrument.
_Avoid_: "occurrence" in user-facing copy (backend/domain term — the UI says "payment"), "pending" (collides with holds / authorisations on the ledger), "upcoming" if it ever drifts from the strict after-today split that defines **Expected**.

**Move money**:
Umbrella nav label for the four new surfaces: **Send money** (one-off **Transfer**), **Payees**, **Direct Debits**, **Standing Orders**. Verb-led precisely to avoid the **"payment"** collision the glossary already flags.
_Avoid_: "Payments" as a nav label, "Pay & transfer" (clunky), "Banking" (vacuous).

**Active AccountHolder**:
The **AccountHolder** the User is currently acting as. Passed on every holder-scoped call via the `MalAccountHolderIdHeader`. In v1 the active holder is resolved as _"the first **AccountHolder** with type `INDIVIDUAL`"_ — every User is guaranteed to have one (see Invariants). A holder switcher will be added when a demo scenario actually surfaces a second holder type.
_Avoid_: "current account" (collides with the UK retail-banking product name).

**Registration**:
The atomic backend flow triggered by `POST /api/v1/auth/register` (inputs: firstName, lastName, email — nothing else) that creates a **User**, a default **Individual AccountHolder**, and that holder's initial **Accounts** in one shot. Address and dateOfBirth are **Generated details**, not user input. A successful register call leaves the **User** in an **Unconfirmed** state until they complete **Verification**; registering with an email that already has a confirmed **User** silently behaves as **Login** (same response, submitted name ignored).
_Avoid_: "sign-up" in code (use in copy is fine), "onboarding" (broader process that includes KYC, not yet in scope).

**Unconfirmed User**:
A **User** who has registered but never completed **Verification**. Both `register` and `login` for an Unconfirmed User's email re-send the **Confirmation code**, and **Verification** from either entry point completes the registration — there is no stuck state and no dedicated "resume" screen; the email field is the lookup key.
_Avoid_: "pending user", "inactive user".

**Login**:
The two-step passwordless flow: `POST /api/v1/auth/login` with just an email triggers a **Confirmation code**; **Verification** with that code signs the **User** in. For an unknown email the response is indistinguishable from a known one — no code arrives, and **Verification** fails generically. There is no password and therefore no password reset.
_Avoid_: "sign-in" in code (fine in user-facing copy), "authentication" (broader — includes the token/cookie machinery that applies after login).

**Confirmation code**:
The short-lived one-time code Cognito emails to a **User** to prove ownership of the email inbox — the sole credential, used identically by **Registration** and **Login**. Submitted via `POST /api/v1/auth/verify-code` (**Verification**); a fresh one is sent by re-submitting `register` or `login` (there is no separate resend affordance). All emails carrying it look the same regardless of which flow triggered it.
_Avoid_: "OTP" (technically correct but unfamiliar in retail-banking copy), "verification token" (overloaded with the auth-token vocabulary), "password" (there isn't one).

**Verification**:
The act of submitting an email + **Confirmation code** to `POST /api/v1/auth/verify-code`. On success the **User** is signed in (session cookies set) — and, if they were an **Unconfirmed User**, their **Registration** is completed in the same step. The backend decides which case applies from the User's state, not from which screen the code came from. All failures (wrong code, expired code, unknown email) produce one generic error.
_Avoid_: "confirmation" on its own (ambiguous with the code), "code exchange" (token-machinery flavour).

**Generated details**:
The profile values the backend invents at **Registration** instead of asking the user: a plausible UK address (the **AccountHolder**'s) and an adult dateOfBirth. Visible later in profile surfaces but never collected by a form.
_Avoid_: "fake data" (breaks the production-bank framing in copy), "defaults" (implies the user could have supplied them).

**Theme**:
A named brand identity in the malai webapp's theme catalogue, represented as a `[data-theme="..."]` block in `src/styles/themes.css` that defines the full design-token set (palette + `--radius`). v1 ships three: `malai` (the default — cream canvas / espresso ink / warm accent), `halcyon` (navy fintech-challenger), `linden` (forest-green incumbent-heritage). The **User** picks one via a header switcher; the choice persists in `localStorage`. Orthogonal to **Mode**. See ADR-0003 for the architecture; ADR-0011 (forthcoming) for the catalogue framing.
_Avoid_: "skin" (informal; ambiguous), "palette" (themes also vary `--radius`), "brand" on its own (collides with the demo's wider rebrand pitch).

**Mode**:
The light / dark axis, orthogonal to **Theme**. Toggled via `next-themes`. Every **Theme** defines both Modes — `[data-theme="X"]` for light and `[data-theme="X"].dark` for dark — both meeting WCAG AA contrast per ADR-0006 #4.
_Avoid_: "dark theme" / "light theme" (Mode is not a Theme — they are independent axes), "appearance" (overloaded with system settings copy).

**Profile tab**:
The mobile bottom-tab destination that aggregates **Settings**, **Sandbox**, **Sign out**, and the **Theme** switcher. On desktop, the same affordances live in the header user-menu dropdown — there is no Profile tab on desktop. Naming follows UK retail-banking convention (Monzo / Starling / Chase all label it "Profile"). Distinct from the two existing "profile" meanings (see Flagged ambiguities).
_Avoid_: "Me" / "You" / "More" tabs (deviate from UK convention without strong reason).

**Sandbox**:
The in-app surface that exposes malai's controllable demo state to the signed-in **User** — the platform-vendor analogue of a fintech sandbox tier, not a consumer-bank feature. Three tabs: **Counterparties** (a read-out of the bank-directory fixtures with their **CoP** behaviour and outbound-payment outcome), **Inbound payments** (a form that triggers a simulated incoming payment to one of the active holder's **Accounts**), and **Cards** (the three card triggers: purchase at a fictional merchant, present-now, merchant refund). Backed by `/api/v1/sandbox/*` on `malai-api`, which fan out to `malai-stub`. Owns the top-level route `/sandbox`; discovery is via the **Profile tab** on mobile and the desktop user-menu dropdown, as a peer of **Settings** — see ADR-0016. Surfacing it honestly _strengthens_ the malai-as-platform pitch — real banking-platform vendors (Mambu / Thought Machine / 10x) expose exactly this shape.
_Avoid_: "Demo" (collides with the broader fact that all of malai is a demo; breaks the production-bank illusion at the wrong altitude), "Developer console" (signals dev-tool, not banking-platform), "Test mode" (implies a global toggle that flips real behaviour off — Sandbox is additive, not modal).

**Card**:
A payment card issued against one of the holder's **Accounts** — `DEBIT` (auto-issued with each current-style Account) or `CREDIT` (arrives only via the **Credit card application**). Lifecycle `ACTIVE ⇄ FROZEN → CANCELLED` (terminal). Virtual-first: born active, no ordering or activation step. The list surface (`GET /api/v1/cards`) carries only the **masked PAN** (last four); the full PAN / expiry / CVV exist solely in the **Reveal**. Owns the top-level `/cards` nav (fourth mobile tab, third desktop link).
_Avoid_: "bank card" (redundant), "plastic" (the card is virtual).

**Reveal**:
The on-demand fetch of one Card's full PAN, expiry, and CVV (`GET /api/v1/cards/{cardId}`), rendered onto the card art until hidden again. Deliberately un-ceremonied — the data is fictional, so there is no step-up auth (PRD decision).
_Avoid_: "show details" in code (the domain verb is reveal), "decrypt" (nothing is encrypted client-side).

**Freeze** / **Unfreeze**:
The reversible customer control that makes a Card decline every authorisation with a visible **Decline reason** (`CARD_FROZEN`) until unfrozen. Freezing never touches already-approved authorisations. The freeze → visible decline → unfreeze loop is a headline demo moment.
_Avoid_: "block" (reads permanent), "lock" (collides with device/passcode language).

**Replacement**:
The outcome of **Report lost or stolen**: the Card is cancelled terminally and a new `ACTIVE` Card with a fresh PAN is issued against the same Account in the same call. One active card per account is preserved throughout.
_Avoid_: "reissue" (bank-ops jargon), "new card order" (implies logistics malai doesn't model).

**Pending / Declined card rows**:
Card-authorisation activity composed server-side into the **Transaction** feed. A **Pending** row is an authorised, not-yet-settled purchase — merchant name, signed amount, no running-balance movement (its ledger Hold has already dropped the available balance). A **Declined** row is a refused purchase — zero balance effect, rendered visually distinct with its **Decline reason** (`CARD_FROZEN`, `CARD_CANCELLED`, `INSUFFICIENT_FUNDS`, `OVER_LIMIT`). Both carry a `cardId` link and neither is categorisable (no ledger entry to pin an Override to). On these rows `entryId` **is** the authorisation id.
_Avoid_: "pending transaction" for anything non-card (the forecast glossary already reserves "pending"), "failed payment" (a decline is a refusal, not a failure).

**Referred payment screen**:
The outcome screen for a **Payment** the bank has parked pre-dispatch (`REFERRED`) — a working spinner, the amount and destination, and the fact that the money is set aside and hasn't left the source **Account**. It polls until a ruling moves the payment, and says nothing about why the payment was parked. The domain concept it renders (**Referral**, **Screening**, **Screening mode**) is owned by `malai-backend/malai-services/payments-service/CONTEXT.md`.
_Avoid_: "under review" / "being reviewed" (names the human step and hints at suspicion), "held" (that's the ledger **Hold**), "checking your details" (implies the customer got something wrong).

**Declined payment screen**:
The outcome screen for a **Payment** the bank refused pre-dispatch (`DECLINED`) — the money never left the source **Account** and nothing reached the **Scheme**. Carries no reason, ever, and deliberately never uses the word "declined" in copy, because naming the refusal invites a "why?" the bank must not answer. Phrased as a refusal ("We didn't make this payment"), never as a failure or an inability.
_Avoid_: "couldn't" / "unable to" / "failed" (a refusal is a decision, not a failure), "declined" in customer-facing copy (see above), any reason, code, or next-step that narrows why.

**Credit card account**:
An **Account** with `accountType = CREDIT_CARD`, created atomically by the **Credit card application** together with its `CREDIT` **Card** at the fixed £2,500 demo limit. Ledger-floored at −limit; carries ordinary sort-code / account-number identifiers so **Pay card** rides the existing book transfer. Its money position is read via the **Credit summary** (`owed`, `available credit`, `limit`, plus the current-cycle statement balance / minimum payment / due date), never via the raw balance.
_Avoid_: "credit account" (ambiguous with accounts in credit), leading the UI with the negative ledger balance (say what is _owed_).

**Pay card**:
The one-screen repayment flow on a **Credit card account** (`/accounts/:accountUid/pay-card`): minimum payment / statement balance / custom presets over the existing **Transfer** machinery, destination resolved from the credit account's own identifiers.
_Avoid_: "repayment" in user-facing copy (say "Pay card"), "make a payment" (the **payment** collision again).

**VELA**:
The fictional card-scheme brand mark rendered on all card art — this repo owns the name (backend vocabulary stays generic "card scheme" per the PRD). Defined once as `SCHEME_MARK` in `src/cards/CardArt.tsx`.
_Avoid_: real scheme names or marks (Visa / Mastercard), varying the spelling.

### Ops surface

The MDR domain language (Entry, Model Decision, Human Disposition, Ruling in force, Actionable output, Outcome, Chain) is owned by `malai-backend/malai-services/mdr-service/CONTEXT.md` — these are only the screen names this app adds.

**Worklist**:
The Ops surface home (`/ops`) — every Model Decision with its verdict on the row, so a reviewer can see what each decision concluded without opening it. The row says its conclusion in whichever vocabulary the verdict speaks: a screening decision with the rule behind it, an AML Alert with its **Typology** named and its risk score; a verdict speaking neither is shown raw rather than guessed at. The awaiting-ruling view (the default) orders oldest-first — the longest-waiting decision is worked first — while ruled views and the Advisory view keep chain order, newest-first. Each view chip carries its depth.
_Avoid_: "queue" (items never leave a record), "list page", inventing a third vocabulary for a verdict shape the app does not know.

**Advisory view**:
The **Worklist** chip that lists Decisions declaring no ruling is expected of them (ADR-0013's `advisory`) — the copilot's explanation records live here, browsable rather than reachable only by record link. Deliberately outside the four ruling-state views, which all pin `advisory=false`.
_Avoid_: mixing advisory rows into the awaiting view (nothing waits on them, so they are not work).

**Decision view**:
The replay read rendered as a record (`/ops/decisions/:decisionId`) — conclusion first: the verdict, then the pinned record (raw output, retrieved-context refs, rendered prompt), then the ruling thread in chain order with the append affordance at its foot; nothing on it is editable. Carries its own decision id at the top, shortened with the full id a copy away.
_Avoid_: "detail page" (generic), "decision form" (it is not a form).

**Ruling draft**:
The append affordance at the foot of the disposition thread — a drafted accept / edit (full replacement) / reject that becomes the next Entry on submit, rendered where that Entry will chain.
_Avoid_: "edit form", any "update"/"save" language (nothing updates; Entries append).

**Gate panel**:
The on-demand panel on the **Decision view** that asks the actionable-output gate and renders either the output in force (with the ruling that authorised it) or the "no ruling in force" state — the 409 displayed as a fact, never toasted as an error.
_Avoid_: "error state" (the refusal is the feature).

**Chain page**:
The dedicated `/ops/chain` screen: verification on demand, the verdict (rows walked, or the failure mode and the seq where the chain breaks), legible to a watching executive.
_Avoid_: "audit page" (the audit plane's chain is a different scope).

**Case list**:
The Cases nav destination (`/ops/cases`) — one row per AML **Case**, newest opened first, the investigation sibling of the **Worklist**. Defaults to every Case with its status worn as a tag — the seeded closed arc must be visible on landing, so no status narrows by default; four chips (Open / Investigating / Closed dismissed / Closed reported) narrow it, each carrying its depth. The Case list and **Case view** keep themselves fresh while on screen — a live-fired pattern lands within a sweep tick with no manual refresh; the **Worklist** deliberately does not poll. The AML domain language (Alert, Case, Typology, Evidence set, SAR draft, Tipping-off) is owned by `malai-backend/malai-services/aml-service/CONTEXT.md` and not restated here.
_Avoid_: "case worklist" (the backend PRD's phrase — **Worklist** is taken by the Model Decision list), "queue".

**Case view**:
The assembled investigation read (`/ops/cases/:caseId`) — the Case's state, its linked Alerts with their evidence, the **SAR panel**, and the two **Close actions**. Alerts are not ruled here: each Alert links to its **Decision view**, where the **Ruling draft** already lives — one ruling affordance in the UI, as the API has one disposition door. A `CASE` correlation ref on the Decision view links back here; `ACCOUNT` and `PAYMENT` refs stay text. An unknown case id renders a designed not-found state on the Decision view's terms, and the poll stops the moment the read errors — a case that is not there is not asked for again every tick.
_Avoid_: "case form", "case detail page" (generic — mirror **Decision view** naming).

**Close actions**:
The two terminal officer actions on the Case view — close as dismissed, close as reported — offered on every non-terminal Case and absent (not disabled) on terminal ones. Two-press inline confirm (first press arms and mints that action's key, second fires, clicking elsewhere disarms, and reaching for the other action drops the first); never pre-disabled by client prediction — the Gate decides, the UI displays, even where the Alert on screen already carries the accept ruling that will refuse the dismissal. Firing disarms, so a retry re-arms and replays the same key: one attempt per action per visit, never a fork. A 409 refusal renders as a designed inline fact whose copy is mapped from the domain code (accepted-alert-stands / no-SAR-ruling-in-force / already-closed), unknown codes falling back generic — an Ops-surface-scoped exception to ADR-0005's no-translation rule, which still binds customer surfaces. A non-409 is not a refusal at all: a request that never reached the bank has ruled nothing, so it renders as the app's own error (the **SAR panel**'s request-failed precedent). The refusal is a polite live region, not an alert — the press changes nothing else on the screen, so the fact has to announce itself (`PaymentOutcome`'s precedent), while staying a fact rather than an error. A landed close refreshes the Case read and the **Case list** rather than letting an officer's own action wait for the next poll.
_Avoid_: toasting a refusal as an error (the refusal is the feature — Gate panel precedent), "delete"/"archive"/"resolve" (a close is a ruled terminal transition, not tidying).

**SAR panel**:
The Case view panel owning the SAR arc. Its states: no draft → the request affordance; a fresh draft → the narrative rendered inline from the invocation response (ephemeral by design — the case read never carries a draft narrative), with grounding refusal and `TIMED_OUT`/`ERRORED` rendered as recorded facts, decision id always linked; on revisit → drafted-status plus ruling-in-force tag linking to the **Decision view** to read and rule; after `CLOSED_REPORTED` → the attached narrative verbatim, permanent. Two further states the arc needs and the record does not name: a request that never reached a record is the app's own error (an alert, no decision link — an ask that failed is not a refusal, the Copilot's precedent), and a Case closed dismissed with no SAR reads as never-drafted rather than borrowing the request affordance a closed Case cannot use. Precedence is the panel's whole argument: the attached narrative outranks a fresh draft (the filed story is permanent), and a fresh draft outranks the record it just wrote (the inline read is the demo's payoff).
_Avoid_: "SAR form", "report viewer", treating the ephemeral inline draft as the record (the record is the MDR entry), a re-draft affordance beside a draft on record (a second invocation forks what the officer is being asked to rule on).

### Exec surface

**Metric tile**:
A named executive number on `/exec` (new customers, deposits, cards issued, **Payments stopped**), reported over the selected **Reporting window**. A tile renders a real figure or an honest em-dash — never a hardcoded value that could end up in a screenshot, and never a zero standing in for an absent number.
_Avoid_: skeleton shimmer for the pending state (implies imminent data), zeroes (a lie with digits), "today" baked into a tile label (the window says that).

**Reporting window**:
The `/exec` control choosing the trailing span every **Metric tile** reports over — Today (the **Banking day**, the default), Last 7 days, Last 30 days, Last 12 months. Every window is anchored to London midnight and ends at the moment of the read, so a figure is stable within a Banking day and a wider window includes today so far. All four metrics are flows, so a wider window changes a query bound, never a metric's meaning; the freshness line names the window's first day ("Since 15 Jul 2026") whenever it is not the day itself.
_Avoid_: calendar periods ("last month" — empty at boundaries, the opposite of the always-some-data goal), rolling windows ("last 24 hours" — a different figure that changes under refresh).

**Payments stopped**:
The fourth **Metric tile**: the value of **Payments** the bank refused after **Screening** review — sourced from the `DECLINED` **Lifecycle** state, which only an enacted adverse ruling produces. Deliberately _not_ called "fraud prevented": the decider is a deterministic rules engine, and a declined payment is money the bank stopped, not proven fraud.
_Avoid_: "fraud prevented"/"fraud caught" (a claim the platform cannot substantiate — retired from the client brief), "rejected payments" (_reject_ is an MDR ruling that stops nothing).

**Banking day**:
The window every **Metric tile** reports over: since midnight `Europe/London`. A UK bank's executive means the London day, not UTC.
_Avoid_: "today" unqualified in code or contract (ambiguous across timezones), "last 24 hours" (a rolling window is a different figure).

**New customers**:
The **Metric tile** counting **AccountHolders** created during the **Banking day**, all four types. Counted as holders rather than **Users** because a staff-only User has no holder and must never inflate the bank's customer count.
_Avoid_: "sign-ups"/"registrations" (**Registration** creates a User _and_ a holder; only the holder is a customer), "new users".

**Deposits**:
The **Metric tile** summing inbound **Payments** that settled during the **Banking day** — money that entered the bank from outside. Excludes **Book transfers** (both legs are internal, so counting them would let a customer inflate the bank's headline figure by moving their own money), card refunds (a reversal of spend), and anything not yet settled. A same-day figure for a flow, so a later **Return** does not retroactively reduce it.
_Avoid_: "credits"/"money in" (would sweep in internal movements), "inflows".

**Metric currency**:
Every money-valued **Metric tile** carries its currency explicitly and reports GBP only in v1; non-GBP **Payments** are excluded from the figure and stated as excluded, never silently summed into it. Minor units of different currencies are not addable.
_Avoid_: a bare total with an assumed currency.

## Relationships

- A **User** holds 1..N **Roles**; each Role **Offers** zero or more **Surfaces** (`CUSTOMER` → Banking; `ADMIN` → Ops + Exec; `MANAGER` → Exec; `SUPPORT_AGENT` → none yet).
- A **User** holding `CUSTOMER` has 1..N reachable **AccountHolders** (1 by default after **Registration**; more via later `POST /api/v1/account-holder`). A staff-only User (no `CUSTOMER` Role) has none.
- An **AccountHolder** has 1..N **Accounts**.
- An **Account** has 0..N **Transactions**, ordered newest-first; the API returns up to 100 most recent with no pagination.
- A **User** has exactly one **Active AccountHolder** at a time within the UI.
- An **AccountHolder** has 0..N **Cards**, each issued against one of their **Accounts**; at most one non-`CANCELLED` Card per Account, and at most one **Credit card account** per holder (v1).
- **Registration** produces: one **User** + one **Individual AccountHolder** + that holder's initial **Accounts**, atomically. The **User** is **Unconfirmed** until **Verification**, which also signs them in.

## Invariants

- **Every User holding the `CUSTOMER` Role has exactly one Individual AccountHolder.** Established by **Registration** and never removed. Staff-only Users (no `CUSTOMER` Role — e.g. the seeded ops reviewer) have no AccountHolders at all, and the Banking surface is **Withheld** from them — so holder resolution only ever runs for `CUSTOMER` Users, where a null result remains an internal error, not an empty state.

## Example dialogue

> **Dev:** After login, what do we land the user on?
> **Domain:** Their **Landing** — the first **Offered Surface** in precedence. For anyone holding `CUSTOMER` that's the Banking surface: the list of **Accounts** for the **Active AccountHolder**.
> **Dev:** And the seeded ops reviewer, who holds only `ADMIN`?
> **Domain:** Banking is **Withheld** — she has no **AccountHolders** to show anyway — so she lands on the **Ops surface**. If she types a Banking URL she gets the **Access page**, not a crash and not a 404.
> **Dev:** And if a customer reaches two holders — say, Individual plus Joint?
> **Domain:** We default to whichever they used last, but the holder switcher has to be visible.

## Flagged ambiguities

- "Declined" now means two unrelated things, with opposite disclosure rules — always qualify it, never write it bare. (a) A **Declined card row** in the **Transaction** feed: a refused card authorisation, rendered with its visible **Decline reason** (`CARD_FROZEN`, `INSUFFICIENT_FUNDS`, …). (b) A **Declined payment**: the `DECLINED` **Lifecycle** state of a **Payment** the bank refused after **Screening**, which carries no reason at all. The card rows have taught the customer that "declined" comes with a why; the payment case deliberately breaks that rule, which is why its screen never uses the word.

- "surface" is now overloaded. Capital-S **Surface** means one of the three siblings (Banking / Ops / Exec). Older entries use lowercase "surface"/"surfaced" informally to mean "screen" or "exposed in the UI" (e.g. **Sandbox** — "the in-app surface…", which is a _screen cluster inside the Banking surface_, not a fourth Surface). New writing should reserve the capitalised term for the siblings and prefer "screen"/"page" otherwise.
- The client brief uses "bank account" and "account" interchangeably. Resolved: **Account** means the money-holding entity from `/api/v1/accounts`. The signed-in person is a **User**; their customer profile is an **AccountHolder**.
- "Profile" is now triply overloaded — qualify every use. The three meanings: (a) **User profile** — `/api/v1/user`, the auth-side profile (email, name, dateOfBirth); (b) **Holder profile** — `/api/v1/account-holder/*`, the customer-side profile (name, address, image); (c) **Profile tab** — the mobile bottom-tab destination aggregating Settings + Sign out + Theme switcher (defined above). Bare "profile" in code or copy is ambiguous.
- Within the **User**'s own profile, two subsets behave differently in the self-service `/settings` surface: **editable** (firstName, lastName — writable via `PUT /api/v1/user`) and **immutable-via-self-service** (email — supplied at **Registration**; dateOfBirth — a **Generated detail**; changes to either would require KYC paperwork in a real bank and are out of scope). The UI renders both subsets but only the editable ones accept input.
