-- V11__create_standing_orders.sql
-- WEB-08 (read-only). Lifecycle deferred to v2.
-- The seeder writes 3 rows for Sarah Mitchell (rent, shared bills).

CREATE TABLE standing_orders (
    standing_order_id  UUID PRIMARY KEY,
    account_id         UUID         NOT NULL REFERENCES accounts (account_id),
    payee_id           UUID         REFERENCES payees (payee_id),  -- nullable for new external payees
    payee_name         VARCHAR(140) NOT NULL,
    payee_sort_code    VARCHAR(8)   NOT NULL,
    payee_account_number VARCHAR(8) NOT NULL,
    reference          VARCHAR(80),
    amount_minor_units BIGINT       NOT NULL,
    currency           VARCHAR(10)  NOT NULL,
    frequency          VARCHAR(20)  NOT NULL,   -- WEEKLY | MONTHLY | ...
    next_payment_at    TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_standing_orders_account ON standing_orders (account_id);
