-- V8__create_idempotency_keys.sql
CREATE TABLE idempotency_keys (
    service             TEXT        NOT NULL,
    key                 TEXT        NOT NULL,
    request_hash        BYTEA       NOT NULL,
    response_status     INT,
    response_headers    JSONB,
    response_body       BYTEA,
    status              TEXT        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (service, key)
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);
