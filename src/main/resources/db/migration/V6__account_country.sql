-- Each card, account and loan belongs to one country. The app shows one
-- country at a time (chosen in the top bar or profile), so a user's US and
-- Indian money never mix. The account's currency follows its country.
ALTER TABLE account
    ADD COLUMN country VARCHAR(2) NOT NULL DEFAULT 'US' CHECK (country ~ '^[A-Z]{2}$');

UPDATE account SET country = 'IN' WHERE currency = 'INR';

-- The dashboard asks for one user's accounts in one country.
CREATE INDEX account_user_country_in_use ON account (user_id, country) WHERE removed_at IS NULL;
