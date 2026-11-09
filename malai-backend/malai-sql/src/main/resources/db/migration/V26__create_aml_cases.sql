-- V26__create_aml_cases.sql
-- AML issue 02: the investigation home. A Case is the single open investigation per
-- subject account: an Alert (a non-advisory Model Decision on the 'mdr' chain) opens
-- one, or attaches to the account's already-open one, atomically with its recording.
-- Alert decision ids and the SAR decision id are opaque refs into the MDR — never
-- foreign keys into mdr_entries, which stays ignorant of its consumers (ADR-0007).
-- Status machine: OPEN → INVESTIGATING → CLOSED_DISMISSED | CLOSED_REPORTED; the
-- terminal transitions are Gate-governed (ADR-0014). sar_narrative is the ruled
-- narrative attached at close-as-reported — stored on the case so a later ruling on
-- the MDR record can never rewrite a closed case's filing.

CREATE TABLE aml_cases (
    case_id            UUID PRIMARY KEY,
    account_id         UUID        NOT NULL,
    status             VARCHAR(20) NOT NULL,
    alert_decision_ids UUID[]      NOT NULL,
    sar_decision_id    UUID,
    sar_narrative      TEXT,
    opened_at          TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    closed_at          TIMESTAMPTZ,

    CONSTRAINT aml_cases_status_values CHECK (status IN ('OPEN', 'INVESTIGATING', 'CLOSED_DISMISSED', 'CLOSED_REPORTED')),
    CONSTRAINT aml_cases_narrative_only_when_reported CHECK (sar_narrative IS NULL OR status = 'CLOSED_REPORTED'),
    CONSTRAINT aml_cases_reported_requires_narrative CHECK (status IS DISTINCT FROM 'CLOSED_REPORTED' OR sar_narrative IS NOT NULL),
    CONSTRAINT aml_cases_reported_requires_sar_decision CHECK (status IS DISTINCT FROM 'CLOSED_REPORTED' OR sar_decision_id IS NOT NULL),
    CONSTRAINT aml_cases_closed_iff_terminal CHECK ((closed_at IS NOT NULL) = (status IN ('CLOSED_DISMISSED', 'CLOSED_REPORTED')))
);

-- One open case per account, enforced at the database: the sweep's open-or-attach
-- races are settled here, defence in depth for the service-level guard.
CREATE UNIQUE INDEX aml_cases_one_open_per_account ON aml_cases (account_id) WHERE status IN ('OPEN', 'INVESTIGATING');

CREATE INDEX idx_aml_cases_opened ON aml_cases (opened_at DESC);
