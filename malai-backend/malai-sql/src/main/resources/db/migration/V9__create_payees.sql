-- V9__create_payees.sql
-- BACK-08: payees support Confirmation of Payee deterministic match.
-- BACK-12: also serves as the saved-beneficiaries table the seeder populates.
-- Owner is the account_holder (NOT the account) -- saved payees are per holder.

CREATE TABLE payees (
    payee_id          UUID PRIMARY KEY,
    account_holder_id UUID         NOT NULL,
    name              VARCHAR(140) NOT NULL,
    sort_code         VARCHAR(8)   NOT NULL,    -- 'XX-XX-XX' formatted
    account_number    VARCHAR(8)   NOT NULL,    -- 8 digits
    nickname          VARCHAR(80),
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID         NOT NULL,
    UNIQUE (account_holder_id, sort_code, account_number)
);

CREATE INDEX idx_payees_holder ON payees (account_holder_id);
-- For CoP lookup: match on sort_code + account_number, then compare name.
CREATE INDEX idx_payees_account_lookup ON payees (sort_code, account_number);
