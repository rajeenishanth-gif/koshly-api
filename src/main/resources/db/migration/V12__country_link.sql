-- Which country accounts belong to the same person. A person registers in one
-- country (their home: the US or Canada) and may add others; each added country
-- is its own Firebase user in that country's tenant, with its own data. This row
-- ties the added account to the home account, so:
--   - the home account's subscription covers it (and limits how many there are),
--   - adding it again on another device signs straight in, without a code,
--   - an added account can't add more countries itself.
-- Only these IDs cross between countries; no money, balances or transactions.
CREATE TABLE country_link (
    -- The added account: one home at most.
    linked_user_id  VARCHAR(128) PRIMARY KEY,
    linked_country  VARCHAR(2)   NOT NULL CHECK (linked_country ~ '^[A-Z]{2}$'),
    home_user_id    VARCHAR(128) NOT NULL REFERENCES app_user (firebase_uid) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- One account per country per person.
    CONSTRAINT country_link_one_per_country UNIQUE (home_user_id, linked_country)
);
