-- V5__create_ledger_entries.sql
-- D-2.6: signed BIGINT amount_minor_units; sum-to-zero per transaction_id
--        enforced via a constraint trigger (CHECK constraints are NOT
--        DEFERRABLE in Postgres).
-- D-2.7: per-counterparty EXTERNAL clearing accounts; the existing
--        accounts.account_type VARCHAR(30) column gains an 'EXTERNAL' value
--        in the application enum -- no DDL change here.
-- D-2.8: single posted_at lifecycle. No pending/settled state.
--
-- The ledger is the bank's book of record and lives behind its own HTTP boundary
-- (ledger-service). The service boundary is HTTP, not the database: ledger tables
-- live in `public` alongside everything else. No FKs from ledger_entries to
-- public.accounts (the ledger references accounts by opaque UUID only); the
-- intra-ledger transaction_id FK and the sum-to-zero deferred trigger stay. A
-- `ledger_accounts` registry (id + currency + kind + guard policy) gates every
-- posting: an account must be registered before anything can post to it.

CREATE TABLE transactions (
    transaction_id    UUID PRIMARY KEY,
    initiated_by_id   UUID         NOT NULL,
    initiated_at      TIMESTAMPTZ  NOT NULL,
    type              VARCHAR(30)  NOT NULL,
    description       VARCHAR(500),
    trace_id          VARCHAR(64)
);

CREATE TABLE ledger_accounts (
    account_id    UUID PRIMARY KEY,
    currency      VARCHAR(10) NOT NULL,
    kind          VARCHAR(20) NOT NULL,
    guard_policy  VARCHAR(20) NOT NULL
);

-- A Hold reserves an amount on a ledger account, reducing its Available balance
-- (ledger - held) without moving its Ledger balance. Lifecycle PLACED -> SETTLED
-- | RELEASED (no expiry). Settle is atomic post-and-release: the hold flips to
-- SETTLED and a balanced debit posting is recorded in one transaction. Release
-- flips to RELEASED and posts nothing. Held amount = SUM over PLACED holds.
CREATE TABLE holds (
    hold_id            UUID PRIMARY KEY,
    account_id         UUID         NOT NULL,
    amount_minor_units BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    placed_at          TIMESTAMPTZ  NOT NULL,
    resolved_at        TIMESTAMPTZ
);

CREATE INDEX idx_ledger_holds_account_status ON holds (account_id, status);

CREATE TABLE ledger_entries (
    entry_id           UUID PRIMARY KEY,
    transaction_id     UUID         NOT NULL REFERENCES transactions (transaction_id),
    account_id         UUID         NOT NULL,
    amount_minor_units BIGINT       NOT NULL,
    posted_at          TIMESTAMPTZ  NOT NULL,
    description        VARCHAR(500),
    counterparty_id    UUID,
    merchant_name      VARCHAR(255),
    category           VARCHAR(50),
    trace_id           VARCHAR(64)
);

CREATE INDEX idx_ledger_entries_account_posted ON ledger_entries (account_id, posted_at DESC);
CREATE INDEX idx_ledger_entries_transaction   ON ledger_entries (transaction_id);

-- Sum-to-zero per transaction enforced via a constraint trigger.
-- Constraint triggers ARE DEFERRABLE INITIALLY DEFERRED -> fire at COMMIT.
CREATE OR REPLACE FUNCTION ledger_entries_sum_to_zero()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT COALESCE(SUM(amount_minor_units), 0)
          FROM ledger_entries
         WHERE transaction_id = COALESCE(NEW.transaction_id, OLD.transaction_id)) <> 0 THEN
        RAISE EXCEPTION 'ledger_entries for transaction_id % do not sum to zero',
            COALESCE(NEW.transaction_id, OLD.transaction_id)
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ledger_entries_sum_to_zero_check
AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION ledger_entries_sum_to_zero();
