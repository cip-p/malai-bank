-- V24__create_card_refunds.sql
-- Cards issue 10: the merchant Refund. A credit back onto a card's Account following a
-- settled purchase, originated by the merchant through the Scheme (stub -> card-gateway ->
-- card-service). One row per refund event, keyed by the scheme's refund_reference so a
-- redelivered refund message is a no-op (the referenced settled purchase can be refunded
-- more than once — each is a distinct refund_reference). A refund never rewrites the
-- original presentment entry; it records a new ledger posting (transaction_id) carrying the
-- original merchant name, resolved from the referenced SETTLED card_authorisations row.

CREATE TABLE card_refunds (
    refund_reference   UUID PRIMARY KEY,
    authorisation_id   UUID         NOT NULL,
    account_id         UUID         NOT NULL,
    scheme_reference   UUID         NOT NULL,
    merchant_name      VARCHAR(255) NOT NULL,
    amount_minor_units BIGINT       NOT NULL,
    currency           VARCHAR(3)   NOT NULL,
    transaction_id     UUID         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL
);
