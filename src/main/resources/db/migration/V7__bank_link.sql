-- One row per bank connection: a Plaid "Item" (one bank login, US) or an
-- Account Aggregator consent (India). Each belongs to one country, so a
-- country's links can be moved or deleted together with its accounts.
CREATE TABLE bank_link (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            VARCHAR(128) NOT NULL REFERENCES app_user (firebase_uid) ON DELETE CASCADE,
    country            VARCHAR(2)   NOT NULL CHECK (country ~ '^[A-Z]{2}$'),
    provider           VARCHAR(20)  NOT NULL CHECK (provider IN ('plaid', 'account_aggregator')),
    status             VARCHAR(10)  NOT NULL DEFAULT 'pending'
                                    CHECK (status IN ('pending', 'active', 'expired', 'revoked', 'error')),
    -- Plaid's item_id, or the Account Aggregator consent handle.
    external_id        VARCHAR(200),
    -- Plaid's access token, encrypted (with Cloud KMS) before it's stored. Never sent to the app.
    secret_ciphertext  TEXT,
    institution_name   VARCHAR(100),
    -- Account Aggregator consents end on a set date; Plaid links don't.
    consent_expires_at TIMESTAMPTZ,
    last_synced_at     TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    removed_at         TIMESTAMPTZ,
    CONSTRAINT bank_link_external_unique UNIQUE (provider, external_id)
);

CREATE INDEX bank_link_user_country_in_use ON bank_link (user_id, country) WHERE removed_at IS NULL;

-- Accounts can now come from either provider, and point to their connection.
ALTER TABLE account ALTER COLUMN source TYPE VARCHAR(20);
ALTER TABLE account DROP CONSTRAINT account_source_check;
ALTER TABLE account ADD CONSTRAINT account_source_check
    CHECK (source IN ('manual', 'plaid', 'account_aggregator'));
ALTER TABLE account ADD COLUMN bank_link_id UUID REFERENCES bank_link (id) ON DELETE SET NULL;
CREATE INDEX account_bank_link ON account (bank_link_id) WHERE bank_link_id IS NOT NULL;
