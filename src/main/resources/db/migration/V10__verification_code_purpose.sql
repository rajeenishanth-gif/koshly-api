-- One-time codes now serve two purposes: "Add a country" (as before) and
-- verifying the mobile number on the user's own account.
ALTER TABLE verification_code
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'add_country'
        CHECK (purpose IN ('add_country', 'phone'));
