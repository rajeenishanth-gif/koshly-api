-- One-time codes for "Add a country": a signed-in user asks for a code at an
-- email address or mobile number, then enters it to open (or create) their
-- separate account in another country. Codes are stored hashed, expire after
-- 10 minutes, allow 5 tries, and can be used once.
CREATE TABLE verification_code (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Who asked (the account they were signed in to), and the country being added.
    user_id      VARCHAR(128) NOT NULL REFERENCES app_user (firebase_uid) ON DELETE CASCADE,
    country      VARCHAR(2)   NOT NULL CHECK (country ~ '^[A-Z]{2}$'),
    channel      VARCHAR(10)  NOT NULL CHECK (channel IN ('email', 'sms')),
    destination  VARCHAR(254) NOT NULL,
    code_hash    VARCHAR(100) NOT NULL,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    expires_at   TIMESTAMPTZ  NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Rate limit: how many codes a user asked for recently.
CREATE INDEX verification_code_user_recent ON verification_code (user_id, created_at);
