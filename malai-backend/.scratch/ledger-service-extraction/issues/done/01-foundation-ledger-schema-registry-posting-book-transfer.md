# Foundation: ledger schema, registry, posting engine — book transfer cut over

Status: done

## Done note (issue 01 complete)

All acceptance criteria met; `mvn install` green across the full reactor (42 modules).

- `ledger` Postgres schema created in V5 (edited in place): `transactions` + `ledger_entries`
  relocated, cross-service FKs to `public.accounts` dropped, intra-ledger `transaction_id` FK +
  V5 sum-to-zero deferred trigger kept (schema-qualified); new `ledger_accounts` registry. V6
  grants `malai_app` on `ledger`; V14 registers the seeded system/suspense accounts.
- `ledger-service` module (PostingEngine + LedgerAccountRegistry + balance projection), bundled
  into `malai-api`, absent from `openapi.yaml`. Kensa acceptance tests cover register +
  postTransaction (happy / guard `LEDGER_INSUFFICIENT_FUNDS` / sum-to-zero / idempotent replay /
  unregistered→4xx) with full-entity DB assertions.
- `malai-ledger-client` + `-test-support` (`WithLedger`) created and registered in root `pom.xml`.
- `account-service.createAccount` registers each new account (CUSTOMER/NO_OVERDRAFT) via the
  ledger client; `malai-seeder` registers seeded customer accounts directly.
- `payments-service` BOOK_TRANSFER posts one balanced SETTLED transaction via the ledger client;
  its outgoing call is asserted via `WithLedger` WireMock fixtures.

Carry-over for later issues (not blockers): FPS/SWIFT/BACS still write the ledger via
account-service's imported entities against the relocated tables (issues 02/03 move them onto
holds/postTransaction, then payments-service can drop the account-service dep). account-service
read side still owns the projection queries (issue 04). The standalone `ledger-service` app
(e2e container, issue 05) is currently unauthed — auth comes from the aggregator's
ServiceJwtFilter when bundled in malai-api. `transactions.idempotency_key` is now redundant
(issue 05 cleanup).

## Parent

`.scratch/ledger-service-extraction/PRD.md`

## What to build

Stand up `ledger-service` as the internal-only book of record and prove the new boundary end-to-end on the simplest flow: a same-bank own→own **book transfer** that completes through the ledger's HTTP API instead of shared persistence.

This slice introduces the **Posting engine** deep module (the heart) and the **ledger-account registry**, the dedicated `ledger` Postgres schema, and the `malai-ledger-client` pair — then cuts book transfer over to use them. A book transfer becomes a single idempotent, funds-guarded, balanced **Posting** (payer → payee, born `SETTLED`, no hold).

Scope:
- New `malai-services/ledger-service` module bundled into `malai-api`; new `malai-ledger-client` + `malai-ledger-client-test-support` under `malai-clients`. Registered in root `pom.xml` (`<modules>` + `<dependencyManagement>`). Absent from `openapi.yaml`.
- Dedicated `ledger` Postgres schema (existing Flyway scripts edited in place, no new files): relocate `transactions` + `ledger_entries`; add `ledger_accounts` (`account_id`, `currency`, `kind`, `guard_policy`); drop the cross-service FKs on `ledger_entries` (→ `accounts`); keep `transaction_id → transactions` and the V5 sum-to-zero deferred trigger.
- `registerLedgerAccount(accountId, currency, kind, guardPolicy)` and `postTransaction(transactionId, entries[], reference, postedAt, traceId)`. The posting locks guarded accounts, checks **Available balance** (equal to **Ledger balance** while no holds exist), enforces sum-to-zero, persists idempotently on `transactionId`, rejects breaches with `LEDGER_INSUFFICIENT_FUNDS` and unregistered accounts with a `4xx`.
- `account-service.createAccount` calls `registerLedgerAccount` (`kind=CUSTOMER`, `guard=NO_OVERDRAFT`); seed registers every seeded customer account.
- `payments-service` book-transfer path stops importing `LedgerEntryEntity`/`TransactionEntity` and calls `postTransaction` via the client instead.

## Acceptance criteria

- [ ] `ledger-service` builds, is bundled into `malai-api`, and is registered in the root `pom.xml`; `malai-ledger-client` + `-test-support` exist and are not referenced in `openapi.yaml`.
- [ ] The `ledger` schema holds `transactions`, `ledger_entries`, and `ledger_accounts`; cross-service FKs to `accounts` are gone; the sum-to-zero deferred trigger still fires; `accounts`/`account_identifiers` stay in `public`.
- [ ] `registerLedgerAccount` creates a registry row; posting to an unregistered account returns a `4xx`.
- [ ] `postTransaction` records a balanced transaction; an unbalanced set is rejected by the DB trigger; a debit that would breach a `NO_OVERDRAFT` account's available balance returns `LEDGER_INSUFFICIENT_FUNDS`; replaying the same `transactionId` is a no-op returning the original result.
- [ ] A book transfer between two of the same holder's accounts succeeds through the ledger client, posts one balanced `SETTLED` transaction, and `payments-service`'s book-transfer code no longer imports `account-service` ledger entities.
- [ ] Kensa acceptance tests cover `registerLedgerAccount` and `postTransaction` (happy path, guard rejection, sum-to-zero rejection, idempotent replay, unregistered→4xx) with full-entity DB assertions; `malai-ledger-client` has request-construction + response-deserialisation tests; the book-transfer cutover asserts the outgoing ledger call via `WithLedger` WireMock fixtures.
- [ ] The demo (book transfer) is green.

## Blocked by

None - can start immediately.
