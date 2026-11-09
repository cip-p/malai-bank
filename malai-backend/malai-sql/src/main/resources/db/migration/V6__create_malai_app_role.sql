-- V6__create_malai_app_role.sql
-- D-2.12: separate Postgres role with INSERT, SELECT only on audit_events.
-- Migrations continue to run as the master role (spring.flyway.* in
-- application.properties); only the application's runtime DataSource uses
-- malai_app.
--
-- The malai_app password comes from a Secrets Manager secret named
-- "mal-<env>-malai-api-app-db" (literal env, e.g. dev/prod). Spring Boot
-- exposes it to Flyway as the placeholder
-- spring.flyway.placeholders.malai_app_password; the standalone
-- MalaiDbMigrator wires the same placeholder from MAL_APP_DB_PASSWORD.
-- The master username (used in ALTER DEFAULT PRIVILEGES) is exposed as
-- spring.flyway.placeholders.db_master_username.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'malai_app') THEN
        EXECUTE format('CREATE ROLE malai_app LOGIN PASSWORD %L', '${malai_app_password}');
    ELSE
        EXECUTE format('ALTER ROLE malai_app WITH LOGIN PASSWORD %L', '${malai_app_password}');
    END IF;
END
$$;

-- 1. Schema usage so the app can resolve table names.
GRANT USAGE ON SCHEMA public TO malai_app;

-- 2. Default privileges for ALL non-audit tables.
GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA public TO malai_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO malai_app;

-- 3. Future tables: ALTER DEFAULT PRIVILEGES so any new table the master role
--    creates automatically grants the right perms to malai_app. The master
--    role name is interpolated from spring.flyway.placeholders.db_master_username
--    (resolved from MAL_DB_MASTER_USERNAME env var; default value is `malai`
--    per malai-infra/.../rds/variables.tf:rds_master_username).
ALTER DEFAULT PRIVILEGES FOR ROLE ${db_master_username} IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO malai_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ${db_master_username} IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO malai_app;
