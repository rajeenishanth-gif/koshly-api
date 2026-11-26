-- Groundwork for using Koshly in more than one country (next: India).

-- The user's country, the currency their totals are shown in, their time zone
-- (so "due today" means today where they live), and the region their data is
-- stored in. Existing users are in the US.
ALTER TABLE app_user
    ADD COLUMN country     VARCHAR(2)  NOT NULL DEFAULT 'US'  CHECK (country ~ '^[A-Z]{2}$'),
    ADD COLUMN currency    VARCHAR(3)  NOT NULL DEFAULT 'USD' CHECK (currency ~ '^[A-Z]{3}$'),
    ADD COLUMN time_zone   VARCHAR(64) NOT NULL DEFAULT 'America/New_York',
    ADD COLUMN data_region VARCHAR(20) NOT NULL DEFAULT 'us';

-- Each card, account or loan has its own currency, so someone can track a US
-- card and an Indian savings account side by side.
ALTER TABLE account
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency ~ '^[A-Z]{3}$');

-- Larger limit for currencies with big numbers (a home loan of 2 crore rupees).
-- NUMERIC(12,2) already allows up to 9,999,999,999.99, so only the API's check changes.
