-- V13__create_shedlock.sql
-- ShedLock JDBC provider's coordination table. Used by net.javacrumbs.shedlock
-- to ensure @Scheduled jobs run on exactly one instance of malai-api at a
-- time when the app is horizontally scaled (ECS Fargate runs N tasks).
--
-- Schema is the JdbcTemplateLockProvider default for Postgres. ShedLock writes
-- with INSERT ... ON CONFLICT (name) DO UPDATE: the row exists forever once
-- created, lock_until advances on each acquire.
--
-- malai_app gets SELECT, INSERT, UPDATE, DELETE on this table via V11's
-- ALTER DEFAULT PRIVILEGES (this is coordination, not append-only data).

CREATE TABLE shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP(3) NOT NULL,
    locked_at   TIMESTAMP(3) NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
