# 03 — Debit card issuance: auto-issue with the account, Cards API, PAN generation

Status: ready-for-agent

## Parent

`.scratch/cards/PRD.md`

## What to build

The birth of `card-service`: a customer registers and their new current account comes with a **Card** — visible in a new customer-facing Cards API with masked PAN, revealable details, and status. This slice creates the bounded context, its client pair, and the malai-api wiring; no spending yet (that's issue 04).

A **Card** is issued against exactly one **Account**, born `ACTIVE` (virtual-first, no ordering/activation), carrying a PAN, expiry, CVV, cardholder name, variant (`DEBIT` in this slice), and status. Debit cards auto-issue when a `PRIMARY` or `ADDITIONAL` account is created — never for savings/loan types — with at most one active card per account. PANs come from the new **PanGenerator** deep module: 16-digit, Luhn-valid, from a dedicated malai BIN in the unassigned 9-series range, with the canonical masked (last-four) form. The PAN joins the audit redactor's patterns so it never lands in logs or the audit chain; card list responses carry the masked form, and the card detail response is the reveal (full PAN, expiry, CVV — fictional data, no step-up).

Domain language: `card-service` CONTEXT.md (**Card**). Backend naming stays scheme-generic; brand is a webapp concern.

## Acceptance criteria

- [ ] OpenAPI edited before controllers: card list and card detail paths, all additive, `oasdiff` stays green
- [ ] New `card-service` module and `malai-card-client` / `malai-card-client-test-support` pair, registered in the root build in **both** the module list and dependency management; `malai-api` depends on `card-service` and imports its configuration; client components get base-url properties in every scanned configuration (known gotcha)
- [ ] Flyway migration for the cards table; entity uses the shared `Currency`-style conventions where applicable and full-entity database assertions in tests
- [ ] Creating a `PRIMARY` or `ADDITIONAL` account auto-issues exactly one `ACTIVE` debit card (registration flow acceptance asserts the card row as a full entity); `SAVINGS` / `LOAN` / other types issue nothing
- [ ] One-active-card-per-account invariant enforced
- [ ] **PanGenerator** unit tests: Luhn validity, malai BIN prefix in the 9-series, 16-digit shape, uniqueness across generations, masked form is last-four-only, expiry/CVV generation
- [ ] Card list endpoint: holder-scoped via the authenticated principal, returns only the holder's cards with masked PAN, variant, status, expiry, linked account reference — strict Kacey `containsProp(json { … noExtraProps() })`
- [ ] Card detail endpoint: ownership-verified (never lookup by uid alone — filter by the authenticated holder; foreign/unknown card → not-found per convention); response includes full PAN, expiry, CVV
- [ ] PAN redaction: audit redactor covers the PAN pattern; a test proves a PAN-bearing payload is redacted in audit output
- [ ] Client methods (list, detail, and an internal issue operation for downstream slices) with WireMock-backed request-construction + deserialisation tests
- [ ] Kensa acceptance happy/sad suites for card-service following the standard service test base

## Blocked by

None - can start immediately

## Completion note (done)

Shipped: the birth of `card-service` (customer Cards API + internal issue endpoint), the
`malai-card-client` / `-test-support` pair, and malai-api wiring. Debit cards auto-issue when a
`PRIMARY`/`ADDITIONAL` account is created.

Key decisions:
- **PanGenerator deep module** — `Luhn` (validity + check digit), `Pan` (mask to last-four,
  `expiryFor` = issuedAt + 3y as MM/YY, `MALAI_BIN = 990432` in the unassigned 9-series),
  `PanGenerator` interface + `RandomPanGenerator` (`@Component`, SecureRandom). Acceptance
  determinism via a `TestPanGenerator` primed through `WithTestPanGenerator`
  (`theNextGeneratedPanWillBe` / `theNextGeneratedCvvWillBe`), `@Bean @Primary` in
  `CardTestConfiguration` — mirrors `TestAccountNumberGenerator`.
- **Idempotent issuance** enforces one-active-card-per-account: `findByAccountIdAndStatus(id,
  ACTIVE)` returns the existing card instead of creating a second; DB partial unique index
  `cards_one_active_per_account ON cards(account_id) WHERE status='ACTIVE'` is defence in depth.
  Re-issue is a no-op that returns the original card (re-runnable seeders/registration).
- **Auto-issuance hook** lives in account-service's two create methods (co-located with the ledger
  register, same service-JWT), right after the identifier save: `AccountsService.createAccount`
  guards on `accountType in {PRIMARY, ADDITIONAL}`; `OnboardingAccountsService.createAccount`
  always issues (always PRIMARY). The card call is byte-identical to the existing ledger-register
  posture (inline, non-compensated).
- **cardholderName = the account's `name`** for this slice (a deliberate v1 placeholder —
  "Personal" on the registration card). Not surfaced by any webapp yet; the detail reveal returns
  it. A later slice should source the real holder name (the orchestrator already computes
  `"${firstName} ${lastName}"`); threading it now would pull auth-service + account-client into the
  blast radius for zero current demo payoff, so it was deferred. Noted in the commit.
- **PAN redaction** — `AuditRedactor` gains value-shape redaction (`(?<!\d)9\d{15}(?!\d)`), so a
  bare malai PAN is stripped from any String value under any key (belt-and-braces over the existing
  key-based `pan`/`cardNumber` match). Verified no existing audit payload carries a 9-series
  16-digit value (grep-checked; 20-digit IBANs don't match — their `9` is digit-preceded).
- **Client `issueCard` (service-token, internal) + `listCards`/`getCard` (access-token, public)** —
  the latter two are built for downstream slices (06/11) and covered by `CardClientTest`; only
  `issueCard` is consumed today.
- No new `MalaiCode` (`CARD_NOT_FOUND(8993)` already existed). No dedicated `CardRepositoryTest`:
  the three derived queries are each exercised by an acceptance test (issue / list / detail),
  matching ledger-service's precedent.

Verification (serial mvn, Postgres+WireMock up; malai_test re-migrated for V22 — tests run with
`spring.flyway.enabled=false`; the task's npm test/typecheck are N/A in this Maven/Kotlin repo):
full reactor compiles+installs clean (`-DskipTests`, EXIT 0); card-service 21/0/0,
malai-card-client 3/0/0, account-service 145/0/0 (incl. the SAVINGS "no card" case + the two
create-path issue-call assertions), malai-security AuditRedactorTest green, registration-seeder
10/0/0 + malai-seeder 5/0/0, malai-api 21/0/0 (aggregator boots with card-service bundled; arch
tests green over the new card paths). OpenAPI additive-only (valid YAML; new tag + 2 paths + 5
schemas) so oasdiff stays green.

Notes for next iteration:
- Issue 04 (first authorised purchase) is now unblocked: it adds `card-gateway` + the
  `AuthorisationDecisioner` + the `card_authorisations` table, calling card-service's internal
  decision surface. The client's `issueCard` pattern and the `WithCards` stub are the templates.
- The cardholder-name placeholder wants a real holder-name source when the webapp Cards screen /
  a name-bearing response lands.
