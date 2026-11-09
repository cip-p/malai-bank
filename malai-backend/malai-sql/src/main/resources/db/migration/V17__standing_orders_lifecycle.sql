-- V17__standing_orders_lifecycle.sql
-- SO/DD lifecycle issue 01: cancellation is a state transition, never a deletion.
-- Backfill: existing rows are ACTIVE; the immutable start_date anchor is derived
-- from the row's next payment date (created_at when next_payment_at is null), so
-- anchor + n * frequency stays consistent with the schedule already on the row.

ALTER TABLE standing_orders
    ADD COLUMN status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN start_date   DATE;

UPDATE standing_orders
   SET start_date = COALESCE((next_payment_at AT TIME ZONE 'UTC')::date,
                             (created_at AT TIME ZONE 'UTC')::date);

ALTER TABLE standing_orders
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN start_date SET NOT NULL;
