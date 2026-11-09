-- V10__create_direct_debits.sql
-- WEB-09 (read-only). Mandate lifecycle deferred to v2.
-- The seeder writes 3 rows for Sarah Mitchell (Council Tax, Sky, Gym).

CREATE TABLE direct_debits (
    direct_debit_id    UUID PRIMARY KEY,
    account_id         UUID         NOT NULL REFERENCES accounts (account_id),
    creditor_name      VARCHAR(140) NOT NULL,
    creditor_sort_code VARCHAR(8)   NOT NULL,
    creditor_account_number VARCHAR(8) NOT NULL,
    reference          VARCHAR(80),
    amount_minor_units BIGINT,                  -- nullable: variable mandates
    currency           VARCHAR(10)  NOT NULL,
    frequency          VARCHAR(20)  NOT NULL,   -- MONTHLY | QUARTERLY | ANNUAL
    next_collection_at TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_direct_debits_account ON direct_debits (account_id);
