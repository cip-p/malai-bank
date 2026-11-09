-- V20__create_category_overrides.sql
-- spending-insights issue 04 (recategorise with live breakdown).
--
-- The Override store owned by insights-service: a customer's explicit Category
-- choice for one ledger entry, keyed by entry_id, that always wins over the
-- rule-derived categorisation on both the feed and the spending breakdown.
-- Lives in `public` per the shared-Flyway convention (one database, one schema);
-- insights-service is the only writer/reader.
--
-- The ledger entry itself is never mutated (it is the uninterpreted book of
-- record) — the Override is insights state. `created_by` is nullable: insights
-- is service-token-only and never resolves the MalaiUser (the PUT body is just
-- {category}); the audited actor for a recategorisation is captured by
-- account-service in the append-only audit_events chain (CATEGORY_OVERRIDDEN).
--
-- No explicit GRANTs: V6 ALTER DEFAULT PRIVILEGES already grants
-- SELECT/INSERT/UPDATE/DELETE on new public tables to malai_app.

CREATE TABLE category_overrides (
    entry_id    UUID PRIMARY KEY,
    category    VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID
);
