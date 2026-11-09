CREATE TABLE accounts
(
    account_id         UUID PRIMARY KEY,
    user_id            UUID         NOT NULL,
    account_holder_id  UUID         NOT NULL,
    account_type       VARCHAR(30)  NOT NULL,
    default_category   UUID         NOT NULL,
    currency           VARCHAR(10)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    name               VARCHAR(255) NOT NULL,
    status             VARCHAR(30) NOT NULL
);

CREATE TABLE account_identifiers
(
    identifier_id      UUID PRIMARY KEY,
    account_id         UUID        NOT NULL REFERENCES accounts (account_id),
    identifier_type    VARCHAR(20) NOT NULL,
    bank_identifier    VARCHAR(34) NOT NULL,
    account_identifier VARCHAR(34) NOT NULL,

    CONSTRAINT account_identifiers_account_type_unique UNIQUE (account_id, identifier_type),
    CONSTRAINT account_identifiers_type_value_unique UNIQUE (identifier_type, account_identifier)
);
