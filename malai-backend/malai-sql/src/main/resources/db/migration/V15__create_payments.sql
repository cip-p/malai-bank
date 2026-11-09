-- V15__create_payments.sql
-- ADR docs/adr/0002-payments-service-bounded-context-and-lifecycle-data-model.md:
-- the `payments` row is the authoritative lifecycle state. `transactions` +
-- `ledger_entries` remain the bookkeeping primitives unchanged (sum-to-zero
-- per transaction_id; V5 deferred trigger).
--
-- Issue 01 of send-and-receive-money/PRD.md: the full set of columns lands
-- now (international columns nullable from the start) even though Book
-- Transfer only populates a subset. Reshaping a table the lifecycle code
-- will soon write to is more expensive than adding nullable columns up front.

CREATE TABLE payments (
    payment_id                   UUID         PRIMARY KEY,

    rail                         VARCHAR(20)  NOT NULL,
    direction                    VARCHAR(10)  NOT NULL,
    state                        VARCHAR(15)  NOT NULL,
    return_reason                VARCHAR(50),

    debtor_account_id            UUID,
    debtor_sort_code             VARCHAR(8),
    debtor_account_no            VARCHAR(8),
    debtor_name                  VARCHAR(255),
    debtor_iban                  VARCHAR(34),
    debtor_bic                   VARCHAR(11),

    beneficiary_account_id       UUID,
    beneficiary_sort_code        VARCHAR(8),
    beneficiary_account_no       VARCHAR(8),
    beneficiary_name             VARCHAR(255),
    beneficiary_iban             VARCHAR(34),
    beneficiary_bic              VARCHAR(11),

    payment_purpose              VARCHAR(255),

    amount_minor_units           BIGINT       NOT NULL,
    currency                     VARCHAR(3)   NOT NULL,
    description                  VARCHAR(500),

    end_to_end_id                UUID         NOT NULL UNIQUE,

    cop_verdict_override         BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at                   TIMESTAMPTZ  NOT NULL,
    created_by_account_holder_id UUID,
    trace_id                     VARCHAR(64),

    screening_decision_id        UUID,
    fx_target_currency           VARCHAR(3)
);

CREATE INDEX idx_payments_beneficiary_account_state_created
    ON payments (beneficiary_account_id, state, created_at DESC);

CREATE INDEX idx_payments_debtor_account_state_created
    ON payments (debtor_account_id, state, created_at DESC);

CREATE INDEX idx_payments_referred_created_at
    ON payments (created_at)
    WHERE state = 'REFERRED';
