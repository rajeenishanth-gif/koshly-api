-- A person's subscription, kept on their home account (the one that pays) and
-- shared by the countries they added (country_link). Filled in by the app stores'
-- and the website's purchase notices once billing is live; until then rows can be
-- added by hand (source 'manual') to test. Checked only when config/app has
-- requireSubscription = true.
CREATE TABLE subscription (
    user_id        VARCHAR(128) PRIMARY KEY REFERENCES app_user (firebase_uid) ON DELETE CASCADE,
    -- 'koshly' (1 country), 'koshly_2' (2 countries), 'koshly_3' (3 countries).
    plan           VARCHAR(30)  NOT NULL,
    -- How many countries the plan covers, home included.
    max_countries  INTEGER      NOT NULL CHECK (max_countries BETWEEN 1 AND 10),
    -- Where it was bought, so the app sends people to the right place to manage it.
    source         VARCHAR(20)  NOT NULL CHECK (source IN ('app_store', 'google_play', 'web', 'manual')),
    -- active: paid up. grace: a payment failed and the store is retrying (still works).
    -- expired: ended, cancelled or refunded.
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('active', 'grace', 'expired')),
    expires_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
