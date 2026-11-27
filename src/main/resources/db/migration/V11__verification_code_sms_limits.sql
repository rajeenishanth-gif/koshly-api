-- Fast counts for the SMS limits: codes per phone number, and texts per day overall.
CREATE INDEX verification_code_destination_recent ON verification_code (destination, created_at);
CREATE INDEX verification_code_channel_recent ON verification_code (channel, created_at);
