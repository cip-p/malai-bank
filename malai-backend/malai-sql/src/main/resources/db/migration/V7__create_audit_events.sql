-- Partitioned tables in PG require all unique constraints (including PK) to
-- include the partition key, hence PRIMARY KEY (event_id, ts) and UNIQUE
-- (seq, ts). The writer's SELECT FOR UPDATE on chain_head ensures globally-
-- monotonic seq in practice, so the loss of the pure-seq UNIQUE is acceptable.
CREATE TABLE audit_events (
    event_id        UUID,
    ts              TIMESTAMPTZ  NOT NULL,
    actor_id        UUID,
    actor_type      VARCHAR(20)  NOT NULL,
    action          VARCHAR(60)  NOT NULL,
    resource_type   VARCHAR(40),
    resource_id     VARCHAR(80),
    before_state    JSONB,
    after_state     JSONB,
    ip              INET,
    user_agent      VARCHAR(500),
    request_id      VARCHAR(64),
    trace_id        VARCHAR(64),
    prev_hash       BYTEA        NOT NULL,
    current_hash    BYTEA        NOT NULL,
    seq             BIGINT       NOT NULL,
    PRIMARY KEY (event_id, ts),
    UNIQUE (seq, ts)
) PARTITION BY RANGE (ts);

-- Indexes on the partitioned parent are auto-propagated to current + future
-- partitions in PG 11+, so the writer/verifier index strategy is unchanged.
CREATE INDEX idx_audit_events_actor_ts ON audit_events (actor_id, ts DESC);
CREATE INDEX idx_audit_events_trace    ON audit_events (trace_id);
CREATE INDEX idx_audit_events_seq      ON audit_events (seq);
CREATE INDEX idx_audit_events_resource ON audit_events (resource_type, resource_id, seq);


CREATE TABLE chain_head (
    scope        VARCHAR(40) PRIMARY KEY,
    last_hash    BYTEA       NOT NULL,
    last_seq     BIGINT      NOT NULL
);

INSERT INTO chain_head (scope, last_hash, last_seq)
VALUES ('global', E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0);


-- Pre-create 24 monthly partitions covering 2026-01 .. 2027-12 inclusive.
-- Operational note: a partition-management job MUST add the next month's
-- partition (and re-apply the per-partition REVOKE in the DO block below)
-- before the pre-created range is exhausted. Otherwise rows fall through
-- to audit_events_default, which then blocks any future range partition
-- whose range overlaps with their ts.
-- SET LOCAL timezone TO 'UTC' so + INTERVAL '1 month' arithmetic on TIMESTAMPTZ
-- doesn't drift across DST boundaries (Europe/London BST shift in late March /
-- October would otherwise produce non-UTC-midnight partition boundaries and
-- collide partition names like audit_events_2026_03 vs _2026_04).
DO $$
DECLARE
m TIMESTAMPTZ := TIMESTAMPTZ '2026-01-01 00:00:00+00';
    end_d TIMESTAMPTZ := TIMESTAMPTZ '2028-01-01 00:00:00+00';
    next_m TIMESTAMPTZ;
    partition_name TEXT;
BEGIN
SET LOCAL timezone TO 'UTC';
WHILE m < end_d LOOP
        next_m := m + INTERVAL '1 month';
        partition_name := 'audit_events_' || to_char(m, 'YYYY_MM');
EXECUTE format(
        'CREATE TABLE %I PARTITION OF audit_events FOR VALUES FROM (%L) TO (%L)',
        partition_name, m, next_m
        );
m := next_m;
END LOOP;
END $$;

CREATE TABLE audit_events_default PARTITION OF audit_events DEFAULT;

-- Re-apply V11 grants. Parent grants cover all queries via the parent (which
-- the writer + verifier always do). Per-partition REVOKE blocks any code
-- path that bypasses the parent by going directly at a child table.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_events FROM PUBLIC, malai_app;
GRANT SELECT, INSERT ON audit_events TO malai_app;

-- Audit table — append-only.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_events FROM PUBLIC, malai_app;
GRANT SELECT, INSERT ON audit_events TO malai_app;

-- chain_head — must be UPDATE-able for chain advance, but NEVER DELETE-able.
GRANT SELECT, UPDATE ON chain_head TO malai_app;
REVOKE DELETE, TRUNCATE ON chain_head FROM PUBLIC, malai_app;

DO $$
DECLARE
rec RECORD;
BEGIN
FOR rec IN
SELECT inhrelid::regclass::text AS partition_name
FROM pg_inherits
WHERE inhparent = 'audit_events'::regclass
    LOOP
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON %s FROM PUBLIC, malai_app', rec.partition_name);
EXECUTE format('GRANT SELECT, INSERT ON %s TO malai_app', rec.partition_name);
END LOOP;
END $$;
