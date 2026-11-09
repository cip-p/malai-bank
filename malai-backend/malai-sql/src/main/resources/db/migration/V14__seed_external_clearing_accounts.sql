INSERT INTO users (
    id, email, first_name, last_name, date_of_birth,
    kyc_status, registration_confirmed, created_at, created_by
) VALUES (
    '00000000-0000-4000-9000-000000000001',
    'system+clearing@malai-internal.invalid',
    'System',
    'Clearing',
    NULL,
    'VERIFIED',
    TRUE,
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    'flyway-V14'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO account_holders (
    account_holder_id, user_id, account_holder_type, status, created_at
) VALUES (
    '00000000-0000-4000-9000-000000000002',
    '00000000-0000-4000-9000-000000000001',
    'INDIVIDUAL',
    'ACTIVE',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
) ON CONFLICT (account_holder_id) DO NOTHING;

-- scheme-settlement account. FPS and SWIFT outbound now settle directly to this account
-- from a native ledger Hold, so the former FPS outbound suspense account (...010001) and
-- SWIFT outbound suspense account (...010004) are retired.
INSERT INTO accounts (
    account_id, user_id, account_holder_id, account_type,
    default_category, currency, created_at, name, status
) VALUES
      ('00000000-0000-4000-8001-000000010002',
       '00000000-0000-4000-9000-000000000001',
       '00000000-0000-4000-9000-000000000002',
       'SUSPENSE',
       '00000000-0000-4000-8009-000000010002',
       'GBP',
       TIMESTAMPTZ '2026-01-01 00:00:00+00',
       'Scheme Settlement',
       'ACTIVE')
    ON CONFLICT (account_id) DO NOTHING;

-- FPS Inbound Suspense account
INSERT INTO accounts (
    account_id, user_id, account_holder_id, account_type,
    default_category, currency, created_at, name, status
) VALUES
    ('00000000-0000-4000-8001-000000010003',
     '00000000-0000-4000-9000-000000000001',
     '00000000-0000-4000-9000-000000000002',
     'SUSPENSE',
     '00000000-0000-4000-8009-000000010003',
     'GBP',
     TIMESTAMPTZ '2026-01-01 00:00:00+00',
     'FPS Inbound Suspense',
     'ACTIVE')
    ON CONFLICT (account_id) DO NOTHING;

-- BACS Inbound Suspense
INSERT INTO accounts (
    account_id, user_id, account_holder_id, account_type,
    default_category, currency, created_at, name, status
) VALUES
    ('00000000-0000-4000-8001-000000010005',
     '00000000-0000-4000-9000-000000000001',
     '00000000-0000-4000-9000-000000000002',
     'SUSPENSE',
     '00000000-0000-4000-8009-000000010005',
     'GBP',
     TIMESTAMPTZ '2026-01-01 00:00:00+00',
     'BACS Inbound Suspense',
     'ACTIVE')
    ON CONFLICT (account_id) DO NOTHING;

-- Register the bank's system/suspense accounts in the ledger registry. They are
-- SYSTEM accounts allowed to go negative so they can carry the bank's position
-- without tripping the funds guard. Posting to an unregistered account is a 4xx,
-- so every account the ledger ever touches must appear here.
INSERT INTO ledger_accounts (account_id, currency, kind, guard_policy) VALUES
    ('00000000-0000-4000-8001-000000010002', 'GBP', 'SYSTEM', 'ALLOW_NEGATIVE'),
    ('00000000-0000-4000-8001-000000010003', 'GBP', 'SYSTEM', 'ALLOW_NEGATIVE'),
    ('00000000-0000-4000-8001-000000010005', 'GBP', 'SYSTEM', 'ALLOW_NEGATIVE')
    ON CONFLICT (account_id) DO NOTHING;
