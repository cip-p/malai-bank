-- V16__create_sqs_message_dedup.sql
-- Issue 02 of send-and-receive-money/PRD.md: SQS message-level idempotency.
-- A duplicate fps.outbound.settled message must not re-apply the lifecycle
-- transition. The settled consumer inserts (messageType, endToEndId) inside
-- the same DB transaction as the state change; PRIMARY KEY violation means
-- "already processed", short-circuit to no-op.

CREATE TABLE sqs_message_dedup (
    message_type    VARCHAR(50)  NOT NULL,
    end_to_end_id   UUID         NOT NULL,
    processed_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (message_type, end_to_end_id)
);
