-- Model Decision Record (MDR) — the append-only, hash-chained plane for AI-assisted
-- decisions and their human rulings (ADR-0007). Second tenant of the platform chain
-- machinery beside the audit plane's 'global' scope: it chains under its own
-- chain_head scope 'mdr', so appends here never contend with the audit chain's lock.
--
-- One heterogeneous table, one chain. entry_kind partitions the row shape:
--   MODEL_DECISION    — a single model invocation (what was asked, what came back).
--   HUMAN_DISPOSITION — a human's ruling on a prior Model Decision (accept / edit / reject),
--                       linked to it via parent_entry_id. Its hash transitively covers the
--                       decision it rules on, so "this human saw exactly that output" is
--                       proven by the chain itself.
--
-- Deliberately NOT partitioned by ts (unlike audit_events): MDR volume is demo-scale, and
-- a plain table keeps a real single-column PK for the self-referencing parent link and
-- avoids inheriting audit's unbuilt partition-management job. Revisit alongside audit's
-- multi-shard work (see .scratch/mdr/plan.md).
CREATE TABLE mdr_entries (
    entry_id                UUID PRIMARY KEY,
    ts                      TIMESTAMPTZ NOT NULL,
    entry_kind              VARCHAR(20) NOT NULL,
    decision_id             UUID        NOT NULL,
    parent_entry_id         UUID REFERENCES mdr_entries (entry_id),
    correlation_type        VARCHAR(40) NOT NULL,
    correlation_id          VARCHAR(80) NOT NULL,

    model_id                VARCHAR(120),
    model_version           VARCHAR(60),
    prompt_template_id      VARCHAR(120),
    prompt_template_version VARCHAR(60),
    prompt_template_hash    BYTEA,
    rendered_prompt         TEXT,
    retrieved_context       JSONB,
    outcome                 VARCHAR(20),
    raw_output              TEXT,
    verdict                 JSONB,
    error_detail            TEXT,
    latency_ms              BIGINT,
    cost_micros             BIGINT,
    cost_currency           VARCHAR(3),

    human_id                UUID,
    disposition             VARCHAR(10),
    edited_output           TEXT,

    prev_hash               BYTEA  NOT NULL,
    current_hash            BYTEA  NOT NULL,
    seq                     BIGINT NOT NULL UNIQUE,

    advisory                BOOLEAN,

    CONSTRAINT mdr_entries_kind_values CHECK (entry_kind IN ('MODEL_DECISION', 'HUMAN_DISPOSITION')),
    CONSTRAINT mdr_entries_outcome_values CHECK (outcome IS NULL OR outcome IN ('COMPLETED', 'TIMED_OUT', 'ERRORED')),
    CONSTRAINT mdr_entries_disposition_values CHECK (disposition IS NULL OR disposition IN ('ACCEPT', 'EDIT', 'REJECT')),

    -- Row shape per kind: a Model Decision carries the model side and never the disposition
    -- side; a Human Disposition carries the disposition side and never the model side.
    CONSTRAINT mdr_entries_kind_shape CHECK (
        (entry_kind = 'MODEL_DECISION'
            AND outcome IS NOT NULL
            AND model_id IS NOT NULL
            AND model_version IS NOT NULL
            AND prompt_template_id IS NOT NULL
            AND prompt_template_version IS NOT NULL
            AND prompt_template_hash IS NOT NULL
            AND rendered_prompt IS NOT NULL
            AND parent_entry_id IS NULL
            AND human_id IS NULL
            AND disposition IS NULL
            AND edited_output IS NULL)
        OR
        (entry_kind = 'HUMAN_DISPOSITION'
            AND parent_entry_id IS NOT NULL
            AND human_id IS NOT NULL
            AND disposition IS NOT NULL
            AND model_id IS NULL
            AND model_version IS NULL
            AND prompt_template_id IS NULL
            AND prompt_template_version IS NULL
            AND prompt_template_hash IS NULL
            AND rendered_prompt IS NULL
            AND retrieved_context IS NULL
            AND outcome IS NULL
            AND raw_output IS NULL
            AND verdict IS NULL
            AND error_detail IS NULL
            AND latency_ms IS NULL
            AND cost_micros IS NULL
            AND cost_currency IS NULL
            AND advisory IS NULL)
    ),

    -- Outcome shape: raw output exists iff the invocation COMPLETED; ERRORED carries a reason.
    -- A missing output (TIMED_OUT / ERRORED) is a recorded fact, not an absent row.
    CONSTRAINT mdr_entries_output_only_when_completed CHECK (raw_output IS NULL OR outcome = 'COMPLETED'),
    CONSTRAINT mdr_entries_completed_requires_output CHECK (outcome IS DISTINCT FROM 'COMPLETED' OR raw_output IS NOT NULL),
    CONSTRAINT mdr_entries_errored_requires_detail CHECK (outcome IS DISTINCT FROM 'ERRORED' OR error_detail IS NOT NULL),

    -- Edit ruling shape: the full edited output is the hash-covered actionable artifact, stored
    -- iff the disposition is EDIT (the diff is derived at read time, never stored).
    CONSTRAINT mdr_entries_edit_requires_output CHECK (disposition IS DISTINCT FROM 'EDIT' OR edited_output IS NOT NULL),
    CONSTRAINT mdr_entries_edited_output_only_on_edit CHECK (edited_output IS NULL OR disposition = 'EDIT')
);

CREATE INDEX idx_mdr_entries_decision_seq ON mdr_entries (decision_id, seq);
CREATE INDEX idx_mdr_entries_correlation ON mdr_entries (correlation_type, correlation_id, ts DESC);
CREATE INDEX idx_mdr_entries_parent ON mdr_entries (parent_entry_id);

-- Append-only at the DB layer, mirroring audit_events (V7): the application role may read and
-- insert but never mutate or delete history. The chain verifier is the supplementary defence
-- against any out-of-band tamper that bypasses these grants.
REVOKE UPDATE, DELETE, TRUNCATE ON mdr_entries FROM PUBLIC, malai_app;
GRANT SELECT, INSERT ON mdr_entries TO malai_app;

-- Genesis chain-head row for the MDR scope: 32 zero bytes, seq 0. The first append links
-- prev_hash = these 32 zero bytes at seq 1, exactly as the audit chain does for 'global'.
INSERT INTO chain_head (scope, last_hash, last_seq)
VALUES ('mdr', E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0);
