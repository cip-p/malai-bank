-- V21__ledger_account_floor.sql
-- Per-account funds-guard floor (cards issue 02). A Ledger account may register
-- FLOORED at a per-account minimum in signed minor units (typically negative -- the
-- credit-card shape, where the floor is -creditLimit). NO_OVERDRAFT and ALLOW_NEGATIVE
-- carry no explicit floor (0 and unbounded respectively), so the column stays NULL for
-- them. The guard rejects any Posting or Hold that would take Available below this floor.

ALTER TABLE ledger_accounts ADD COLUMN floor_minor_units BIGINT;
