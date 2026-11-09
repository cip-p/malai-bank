# 12 — Seeders and ambient card spend

Status: done (stub ambient half + registration debit card in part 1; flagship credit estate + PAN registration in part 2)

## Parent

`.scratch/cards/PRD.md`

## What to build

Cards exist before anyone touches anything. **Fresh registrations** (registration seeder): the auto-issued debit card is backdated to sit consistently with the account's seeded history — which already reads as card spend via the merchant-named entries — and there is deliberately **no credit card**, so the application flow stays a live demo moment. **Flagship demo users** (the environment seeder): the full estate — debit + credit cards, months of merchant-rich credit history, a part-paid current statement, and one or two live pending authorisations so the feed shows `PENDING` rows on first open.

The stub gains a **PAN registry** and an **ambient card-spend scheduler** (the BACS payroll pattern): the flagship seeder registers its cards' PANs with the stub, and the scheduler originates small merchant purchases against registered PANs on a configurable cadence — so seeded accounts feel alive between demonstrator clicks. Live-registered users are never targeted (their PANs are unregistered); they drive spend via the sandbox.

## Acceptance criteria

- [x] Registration seeder: a fresh user ends with exactly one `ACTIVE` debit card per current-style account, `createdAt` backdated consistently with the account's seeded history; no credit card exists — asserted in the seeder's integration tests per existing patterns
- [x] Flagship seeder: debit + credit cards, a `CREDIT_CARD` account with several months of settled merchant history, a part-paid current statement (statement figures non-trivial: owed > 0, minimum > £5 branch exercised), and one or two `AUTHORISED` pending authorisations visible in the composed feed
- [x] Flagship seeder registers its PANs with the stub's PAN registry (outgoing registration asserted in seeder tests)
- [x] Seeded pending authorisations are real card-service authorisations with real ledger holds (available balance reflects them), not display fakes
- [x] Stub PAN registry: register/list semantics tested; ambient scheduler originates purchases **only** against registered PANs, on the configured cadence, choosing merchants from the fictional directory; ambient can be disabled by configuration; scheduler tests follow the existing ambient-payroll pattern including thread/test-id discipline
- [x] Ambient-originated purchases flow through the real gateway → card-service path (nothing posts directly)
- [x] Seeder runs remain idempotent/re-runnable per their existing contracts

## Progress note (2026-07-12)

**Landed (green):**
- **Stub PAN registry** — `StubPanRegistry` (in-memory, register/dedup/list; `com.malai.stub.directory`), `POST /scenarios/cards/pans` + `GET /scenarios/cards/pans` on `StubCardSchemeController`. `StubPanRegistryTest` (unit) + a register/list acceptance case in `StubCardSchemeAcceptanceTest`.
- **Ambient card scheduler** — `StubAmbientCardScheduler` mirrors the BACS payroll pattern: `@Scheduled(cron)`, `@ConditionalOnProperty(malai.stub.ambient.card.enabled=true)` (default off → disabled-by-config), rotates merchant + amount deterministically across registered PANs, calls `StubCardScheme.originateAuthorisation` (the real stub→gateway→card-service path — nothing posts directly). Env bindings `MAL_STUB_AMBIENT_CARD_{ENABLED,CRON}` in `application.yaml`. `StubAmbientCardSchedulerTest` (unit, mirrors `StubAmbientActivitySchedulerTest`). PANs masked in logs.
- **Registration seeder debit card** — `SeedPan` (Luhn/BIN-990432/CVV/expiry, deterministic) + `CardSeedHelper` (raw-SQL `cards` insert + `SEED_CARD_ISSUED` audit event, backdated `created_at`, like `LedgerSeedHelper`). `RegistrationSeederService.doSeed` issues exactly one `ACTIVE` `DEBIT` card per CURRENT account, backdated to `now-31d`, **no** credit card. Drawn last so the byte-stable Sarah dataset (amounts/instrument UUIDs) is untouched. IT updated: `cards` in TRUNCATE + `clearSeededData`, audit-chain +1 + `SEED_CARD_ISSUED` assertion, new backdated-debit-card test. `SeedPanTest` (unit). This debit card also reaches the flagship (Sarah) because `SeedCommand` delegates the CURRENT account to `registrationSeederService.seedFor`.
- **Non-regression** — `CleanCommand` now deletes `card_refunds`/`card_authorisations`/`holds`/`cards` (Sarah-scoped) so `reset` stays idempotent now that Sarah gets a card.

Verified: malai-stub 73/73, malai-registration-seeder 17/17, malai-seeder 5/5, malai-api seed/registration acceptance 5/5 (aggregator boots with `CardSeedHelper` wired; real registration→hook→seed issues the card end-to-end).

**Remaining (flagship credit estate + PAN registration wiring):**
- `SeedCommand` (malai-seeder): create a `CREDIT_CARD` `AccountEntity` + ordinary identifiers + ledger account registered `FLOORED` at `-250000` (mirror `AccountsService.applyForCreditCard`; note `seedUserAccount` currently hard-codes `NO_OVERDRAFT` — parameterise the guard policy/floor), issue a `CREDIT` card via `CardSeedHelper.issueCard(variant="CREDIT")`, post several backdated settled merchant purchases on the credit account via `LedgerSeedHelper.postBalancedPair` (negative = owed) netting to **owed > £200** (minus a partial repayment credit) so the `minimum > £5` (2.5%) `StatementCalculator` branch fires, and insert 1–2 real pending authorisations: `card_authorisations` (AUTHORISED, `hold_id = authorisation_id`, backdated `created_at`) **+** matching `holds` (PLACED) rows on the debit account so available balance genuinely drops (available = ledger − Σ PLACED holds). Add deterministic UUID constants to `SarahMitchellSeed`.
- **PAN registration** (`Slice B`): add `MalaiStubClient.registerCardPan(pan)` → `POST /scenarios/cards/pans` + `WithMalaiStub` support + `MalaiStubClientTest`; add `malai-stub-client` dep + `malai.stub.base-url` to `malai-seeder`; `SeedCommand` registers the debit + credit PANs (best-effort/fail-open). `CardSeedHelper.issueCard` already returns `SeededCard(cardId, pan)` for exactly this.
- **Tests**: no DB-backed `SeedCommand` integration test exists today (only the pure-unit `SeedUserCommandTest`) — the flagship ACs need one built (full context + Cognito mock, `RegistrationSeederServiceIntegrationTest` is the closest model), asserting the credit estate as full rows + the outgoing PAN registration (WireMock).

## Completion note (2026-07-12, part 2 — flagship credit estate + PAN registration)

**Landed (green):**
- **`MalaiStubClient.registerCardPan(pan)`** → `POST /scenarios/cards/pans` (202), `RegisterCardPanPayload`. `WithMalaiStub.registerCardPanAccepts()` + `MalaiStubClientTest.registersACardPan` (asserts the outgoing POST body). No pom/property wiring was needed: `MalaiStubClient` is already a scanned bean in the seeder context (transitively via `account-service` → `MalaiClientConfiguration @ComponentScan("com.malai.client.malai")`) and `malai.stub.base-url` was already set — SeedCommand just injects it.
- **Seeder card helpers** — `LedgerSeedHelper.placeHold` (raw `holds` insert, PLACED, backdated) and `CardSeedHelper.placeAuthorisation` (raw `card_authorisations` insert, AUTHORISED, `hold_id = authorisation_id`, backdated, PAN-free, + `SEED_CARD_AUTHORISATION` audit event).
- **`SeedCommand` flagship estate** — a `CREDIT_CARD` `AccountEntity` (`CREDIT_ACCOUNT_ID`) + ordinary SORT_CODE identifier + ledger account registered `FLOORED` at `-250000` (raw SQL, `ON CONFLICT DO NOTHING`); a `CREDIT` card via `CardSeedHelper.issueCard` (pinned `CREDIT_CARD_ID`, deterministic PAN off `CREDIT_CARD_PAN_SEED`); six backdated settled merchant purchases (fictional-directory names, £440.00) less a £90.00 repayment credit → ledger balance −£350.00 (owed £350.00, so the 2.5% minimum branch fires, not the £5 floor); two real pending authorisations on the CURRENT account (`card_authorisations` AUTHORISED against the real debit card + PLACED `holds`, £52.00 + £18.75 held). PANs registered with the stub (debit queried from the DB, credit from the `SeededCard`) — **fail-open** so a stub outage never fails the seed. New `SarahMitchellSeed` constants (index 7+).
- **DB-backed `SeedCommandIntegrationTest`** (new `TestSeederApplication` + `application-test.properties`, Cognito disabled by blank pool-id, `@MockitoBean MalaiStubClient`): credit account + FLOORED ledger + identifier, CREDIT card, owed>£200 / 2.5% branch, two AUTHORISED auths + matching PLACED holds, both PANs registered (Mockito verify), audit chain Ok, and idempotent re-seed after `clean` (no `cards_one_active_per_account` violation).
- **`CleanCommand`** needed no change — its existing account-scoped deletes of `card_authorisations`/`holds`/`cards` already cover the new credit account + pending auths (proven by the re-seed test).

Verified (serial mvn, Postgres up): `malai-stub-client` 10/10, `malai-seeder` 12/12 (7 new IT + 5 unit), `malai-registration-seeder` 17/17 (helpers additive, unaffected), full reactor `install -DskipTests` EXIT 0, `malai-api` 21/21 (aggregator boots with the changed client).

## Blocked by

- `.scratch/cards/issues/09-credit-statement-and-repayment.md`
- `.scratch/cards/issues/10-reversal-and-merchant-refund.md`
