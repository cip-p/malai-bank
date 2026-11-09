CREATE TABLE account_holders
(
    account_holder_id   UUID PRIMARY KEY,
    user_id             UUID        NOT NULL,
    account_holder_type VARCHAR(30) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    created_at          TIMESTAMP   NOT NULL
);

CREATE TABLE individuals
(
    individual_id     UUID PRIMARY KEY,
    account_holder_id UUID         NOT NULL REFERENCES account_holders (account_holder_id),
    title             VARCHAR(50),
    first_name        VARCHAR(255) NOT NULL,
    last_name         VARCHAR(255) NOT NULL,
    date_of_birth     DATE         NOT NULL,
    email             VARCHAR(255) NOT NULL,
    phone             VARCHAR(50)  NOT NULL,
    status            VARCHAR(30)  NOT NULL
);

CREATE INDEX idx_individuals_account_holder_id ON individuals (account_holder_id);

CREATE TABLE businesses
(
    business_id                 UUID PRIMARY KEY,
    account_holder_id           UUID         NOT NULL REFERENCES account_holders (account_holder_id),
    company_name                VARCHAR(255) NOT NULL,
    company_type                VARCHAR(100),
    company_category            VARCHAR(100),
    company_sub_category        VARCHAR(100),
    company_registration_number VARCHAR(50),
    email                       VARCHAR(255) NOT NULL,
    phone                       VARCHAR(50)  NOT NULL,
    status                      VARCHAR(30)  NOT NULL
);

CREATE INDEX idx_businesses_account_holder_id ON businesses (account_holder_id);

CREATE TABLE business_addresses
(
    address_id   UUID PRIMARY KEY,
    business_id  UUID         NOT NULL REFERENCES businesses (business_id),
    address_type VARCHAR(20)  NOT NULL,
    line1        VARCHAR(255) NOT NULL,
    line2        VARCHAR(255),
    line3        VARCHAR(255),
    post_town    VARCHAR(255) NOT NULL,
    post_code    VARCHAR(20)  NOT NULL,
    country_code VARCHAR(2)   NOT NULL
);

CREATE INDEX idx_business_addresses_business_id ON business_addresses (business_id);

CREATE TABLE sole_traders
(
    sole_trader_id        UUID PRIMARY KEY,
    account_holder_id     UUID         NOT NULL REFERENCES account_holders (account_holder_id),
    trading_as_name       VARCHAR(255) NOT NULL,
    business_category     VARCHAR(100),
    business_sub_category VARCHAR(100),
    status                VARCHAR(30)  NOT NULL
);

CREATE INDEX idx_sole_traders_account_holder_id ON sole_traders (account_holder_id);

CREATE TABLE profile_images
(
    profile_image_id  UUID PRIMARY KEY,
    account_holder_id UUID        NOT NULL UNIQUE REFERENCES account_holders (account_holder_id),
    content_type      VARCHAR(50) NOT NULL,
    image_data        BYTEA       NOT NULL
);

CREATE TABLE account_holder_addresses
(
    address_id        UUID PRIMARY KEY,
    account_holder_id UUID         NOT NULL REFERENCES account_holders (account_holder_id),
    line1             VARCHAR(255) NOT NULL,
    line2             VARCHAR(255),
    line3             VARCHAR(255),
    post_town         VARCHAR(255) NOT NULL,
    post_code         VARCHAR(20)  NOT NULL,
    country_code      VARCHAR(2)   NOT NULL,
    udprn             VARCHAR(255),
    umprn             VARCHAR(255),
    from_date         DATE         NOT NULL,
    is_current        BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE settle_up_profiles
(
    settle_up_profile_id UUID PRIMARY KEY,
    account_holder_id    UUID         NOT NULL UNIQUE REFERENCES account_holders (account_holder_id),
    settle_up_link       VARCHAR(255) NOT NULL,
    status               VARCHAR(20)  NOT NULL
);

