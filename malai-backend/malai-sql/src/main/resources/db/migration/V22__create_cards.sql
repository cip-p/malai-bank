-- V22__create_cards.sql
-- Cards issue 03: the birth of card-service. A Card is issued against exactly one
-- Account (account_id) and owned by one AccountHolder (account_holder_id, the scoping
-- key for the customer-facing list/detail reads). Born ACTIVE (virtual-first, no
-- ordering/activation). PAN / expiry / CVV are fictional; the PAN is masked to the last
-- four everywhere except the explicit reveal, and never lands in logs or the audit chain.
-- variant is DEBIT in this slice (CREDIT arrives with the credit card application).

CREATE TABLE cards (
    card_id           UUID PRIMARY KEY,
    account_id        UUID         NOT NULL,
    account_holder_id UUID         NOT NULL,
    cardholder_name   VARCHAR(255) NOT NULL,
    pan               VARCHAR(19)  NOT NULL,
    expiry            VARCHAR(5)   NOT NULL,    -- 'MM/YY'
    cvv               VARCHAR(4)   NOT NULL,
    variant           VARCHAR(20)  NOT NULL,    -- DEBIT | CREDIT
    status            VARCHAR(20)  NOT NULL,    -- ACTIVE | FROZEN | CANCELLED
    created_at        TIMESTAMPTZ  NOT NULL
);

-- Enforce the one-active-card-per-account invariant at the database, defence in depth for
-- the registry's find-then-issue guard. Cancelled cards (lost/stolen replacements) leave
-- their row behind, so the constraint is partial on the ACTIVE status.
CREATE UNIQUE INDEX cards_one_active_per_account ON cards (account_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_cards_account_holder ON cards (account_holder_id);
