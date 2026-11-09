-- V18__direct_debits_lifecycle.sql
-- SO/DD lifecycle issue 02: mandate cancellation is a state transition, never a deletion.
-- Backfill: existing rows are ACTIVE; the immutable start_date anchor is derived
-- from the row's next collection date (created_at when next_collection_at is null),
-- so anchor + n * frequency stays consistent with the schedule already on the row.

ALTER TABLE direct_debits
    ADD COLUMN status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN start_date   DATE;

UPDATE direct_debits
   SET start_date = COALESCE((next_collection_at AT TIME ZONE 'UTC')::date,
                             (created_at AT TIME ZONE 'UTC')::date);

ALTER TABLE direct_debits
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN start_date SET NOT NULL;
