-- V23__create_card_authorisations.sql
-- Cards issue 04: the issuer-side Card authorisation. One row per merchant authorisation
-- request the bank decides on synchronously (stub -> card-gateway -> card-service): an
-- AUTHORISED row carries an active ledger hold (hold_id) on the card's account, a DECLINED
-- row carries a decline_reason and no hold. card_id / account_id / account_holder_id are
-- null for an UNKNOWN_CARD decline (the PAN matched no card). scheme_reference is the
-- scheme's correlation id (the later presentment/reversal legs reference it). mcc is stored
-- for realism but drives nothing in v1. The pan never lands here — it is resolved to a card
-- and discarded, so the authorisation row is PAN-free.

CREATE TABLE card_authorisations (
    authorisation_id   UUID PRIMARY KEY,
    card_id            UUID,
    account_id         UUID,
    account_holder_id  UUID,
    scheme_reference   UUID         NOT NULL,
    merchant_name      VARCHAR(255) NOT NULL,
    mcc                VARCHAR(4)   NOT NULL,
    amount_minor_units BIGINT       NOT NULL,
    currency           VARCHAR(3)   NOT NULL,
    status             VARCHAR(20)  NOT NULL,    -- AUTHORISED | DECLINED
    decline_reason     VARCHAR(30),              -- null unless DECLINED
    hold_id            UUID,                     -- null unless AUTHORISED
    created_at         TIMESTAMPTZ  NOT NULL
);

-- The account feed (issue 06) reads an account's in-flight and declined authorisations.
CREATE INDEX idx_card_authorisations_account ON card_authorisations (account_id);
