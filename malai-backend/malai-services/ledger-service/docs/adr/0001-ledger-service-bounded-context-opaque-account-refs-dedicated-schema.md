# `ledger-service` owns the book of record; accounts are opaque references across a dedicated schema

The ledger — `transactions`, `ledger_entries`, balance projections, and the bank's internal/system accounts — is extracted out of `account-service` into a new `ledger-service`, exposed over an internal HTTP API. It is designed as a **swappable book-of-record**: the API is the seam where a real bank would wire in an external core ledger (Mambu) instead of malai's own. The ledger treats every `accountId` as an **opaque UUID** — no foreign key back to `accounts`, no knowledge of account holders, sort codes, or onboarding — and its tables live in a **dedicated `ledger` Postgres schema** so the boundary is physically real, not merely conventional. Customer accounts are *registered* with the ledger (a `ledger_accounts` row: id + currency + kind + guard policy) when `account-service` creates them; system accounts are seeded.

## Context

This is not greenfield. `account-service` owned `LedgerEntryEntity`/`TransactionEntity` and their repositories, and `payments-service` reached **straight into them** — importing the entities and calling `ledgerEntryRepository.save(...)` directly, with a Maven dependency on `account-service` whose own comment admitted it was reaching the book-of-record. ADR-0002 (root) knowingly took on that boundary violation. This ADR pays it down and sets up the future "external ledger like Mambu, or our own" optionality the brief calls for.

## Considered options

- **Mambu-shaped — opaque account references, dedicated schema** *(accepted).* The only model that honours the Mambu north star: the future swap becomes a client reimplementation, not surgery. Forces the cross-service consistency problem (see ADR-0002) to be solved properly now rather than hidden behind a shared DB transaction that wouldn't survive a real external ledger. A dedicated `ledger` schema makes "no FK to `accounts`" enforce itself — a cross-service reference would have to be written on purpose.
- **Shared `accounts` table, keep the FK** *(rejected).* Least churn, easy atomicity *while* the DB is shared — but it is not a real boundary (can't swap for Mambu without untangling the FK) and leaves the door open to exactly the direct-DB reach being removed.
- **Ledger owns `accounts` outright** *(rejected).* Then directory, identifiers (sort code/IBAN), CoP, and ownership-by-account-holder all drag along — that is not a ledger, it is `account-service` renamed.
- **Same schema, FKs dropped by convention** *(rejected in favour of a dedicated schema).* Expresses the boundary in prose only; the FK temptation is one `ALTER` away.

## Consequences

- New module `malai-services/ledger-service` bundled into `malai-api`, with a `malai-ledger-client` / `malai-ledger-client-test-support` pair. Registered in root `pom.xml` (`<modules>` + `<dependencyManagement>`). **Internal-only — absent from the hand-authored `openapi.yaml`** (the webapp contract); it follows the `malai-account-client` precedent.
- A `ledger_accounts` registry is introduced. `account-service`'s account-creation path gains one internal call, `registerLedgerAccount`. Posting to an unregistered account is a `4xx`, not silent money creation.
- `account-service` keeps its internet-facing balance/transactions/statement controllers but becomes a thin composer: the running-balance window query and statement opening/closing **move into `ledger-service`**; `account-service` calls `getBalances`/`listEntries` and wraps the result with account metadata. `BalanceResponse` in `openapi.yaml` gains an `available`/`held` split (the only webapp-visible change; URLs do not move).
- `transactions`, `ledger_entries`, `holds`, `ledger_accounts` move to a `ledger` Postgres schema; `accounts`/`account_identifiers` stay in `public`. Cross-service FKs on `ledger_entries` are dropped; `transaction_id → transactions` and the V5 sum-to-zero deferred trigger stay (both ledger-internal). Clean-room latitude permits editing existing Flyway scripts in place — no new migration files. Entry metadata (`description`, `counterpartyId`, `merchantName`, `category`) stays on the entry as **opaque passthrough** the ledger never interprets.
- Seeders (`malai-seeder`, `malai-registration-seeder`) must register seeded accounts in `ledger_accounts` and write to the `ledger` schema.
- `ledger-service` joins the cross-stack e2e as its own container behind the in-JVM WireMock reverse-proxy.
- `CONTEXT-MAP.md` gains a `ledger-service` context; **Ledger**, **Ledger account**, **Ledger entry**, and **Transaction** move there from `account-service`.
