CREATE TABLE users
(
    id                     UUID PRIMARY KEY,
    email                  VARCHAR(255) NOT NULL,
    first_name             VARCHAR(255),
    last_name              VARCHAR(255) NOT NULL,
    date_of_birth          DATE,
    kyc_status             VARCHAR(30)  NOT NULL,
    registration_confirmed BOOLEAN      NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL,
    created_by             VARCHAR(255) NOT NULL,
    modified_at            TIMESTAMPTZ,
    modified_by            VARCHAR(255)
);

CREATE UNIQUE INDEX users_email ON users (email);
