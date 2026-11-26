-- Removing an account hides it right away and deletes it for good after a
-- retention period (30 days by default; see koshly.retention.* settings).
-- removed_at is NULL for accounts that are in use.
ALTER TABLE account ADD COLUMN removed_at TIMESTAMPTZ;

-- A removed Plaid account may be linked again while the old row waits to be
-- deleted, so Plaid ids only need to be unique among accounts in use.
ALTER TABLE account DROP CONSTRAINT account_plaid_account_id_key;
CREATE UNIQUE INDEX account_plaid_account_id_in_use
    ON account (plaid_account_id)
    WHERE removed_at IS NULL;

-- Lets the nightly clean-up find old removed accounts quickly.
CREATE INDEX account_removed_at ON account (removed_at) WHERE removed_at IS NOT NULL;
