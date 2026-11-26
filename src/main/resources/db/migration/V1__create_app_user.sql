-- One row per person who has used the API, linked to their Firebase account.
-- Flyway runs this file once. Never edit it after it has run: add V2__... instead.
CREATE TABLE app_user (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid VARCHAR(128) NOT NULL UNIQUE,
    email        VARCHAR(254),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
