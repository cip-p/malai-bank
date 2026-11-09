-- Account number generator. UK convention: 8 digits per account number, unique within
-- a sort code. Malai operates a single sort code (65-82-37), so a single global
-- monotonic sequence is sufficient — no per-sort-code partitioning needed today. If we
-- ever onboard a second sort code, we'd add `account_number_seq_<sort_code>` and route
-- the generator by sort code; until then this is overkill.
--
-- Starting at 10000000 keeps every value an 8-digit number without zero-padding (so
-- the format string in [AccountNumberGenerator] is purely defensive, never strictly
-- needed). The max is the largest 8-digit number — long before exhaustion we'd need
-- a second sort code anyway.
--
-- Sequences are non-transactional in Postgres: `nextval` increments and returns
-- a unique value even if the surrounding tx rolls back. That's exactly what we want
-- for monotonic, collision-free identifiers — concurrent transactions can never see
-- the same value. The unique constraint on `account_identifiers (identifier_type,
-- account_identifier)` is kept as defense in depth.
CREATE SEQUENCE account_number_seq
    AS BIGINT
    START WITH 10000000
    INCREMENT BY 1
    MINVALUE 10000000
    MAXVALUE 99999999;
