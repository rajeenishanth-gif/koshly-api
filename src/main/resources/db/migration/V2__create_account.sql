-- Every card, bank account and loan is one row in "account" (the shared
-- fields). Credit cards and loans add a row in their detail table. Same
-- shape as Plaid: type = depository | credit | loan, plus a subtype.
-- The CHECK rules repeat the API's validation inside the database.

CREATE TABLE account (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          VARCHAR(128)  NOT NULL REFERENCES app_user (firebase_uid) ON DELETE CASCADE,
    type             VARCHAR(20)   NOT NULL,
    subtype          VARCHAR(20)   NOT NULL,
    name             VARCHAR(40)   NOT NULL,
    institution      VARCHAR(40)   NOT NULL,
    last4            VARCHAR(4)    CHECK (last4 ~ '^[0-9]{4}$'),
    balance          NUMERIC(12,2) NOT NULL CHECK (balance >= 0),
    source           VARCHAR(10)   NOT NULL DEFAULT 'manual' CHECK (source IN ('manual', 'plaid')),
    plaid_account_id VARCHAR(100)  UNIQUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT account_type_and_subtype CHECK (
        (type = 'depository' AND subtype IN ('checking', 'savings'))
        OR (type = 'credit'  AND subtype = 'credit_card')
        OR (type = 'loan'    AND subtype IN ('auto', 'student', 'mortgage', 'personal'))
    ),
    -- Cards and bank accounts always have last 4 digits; loans may not
    CONSTRAINT account_last4_required CHECK (type = 'loan' OR last4 IS NOT NULL),
    -- Lets the detail tables below insist on the right type (see account_type there)
    CONSTRAINT account_id_type_unique UNIQUE (id, type)
);

-- Every query looks accounts up by user, so index that column
CREATE INDEX account_user_id_idx ON account (user_id);

-- Extra details for type = 'credit'. The (account_id, account_type) key means
-- a row here can only ever belong to a credit account.
CREATE TABLE credit_card_detail (
    account_id        UUID          PRIMARY KEY,
    account_type      VARCHAR(20)   NOT NULL DEFAULT 'credit' CHECK (account_type = 'credit'),
    credit_limit      NUMERIC(12,2) NOT NULL CHECK (credit_limit > 0),
    statement_balance NUMERIC(12,2) NOT NULL CHECK (statement_balance >= 0),
    minimum_due       NUMERIC(12,2) NOT NULL CHECK (minimum_due >= 0 AND minimum_due <= statement_balance),
    due_day           INTEGER       NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    apr               NUMERIC(5,2)  NOT NULL DEFAULT 0 CHECK (apr BETWEEN 0 AND 100),
    color             VARCHAR(7)    NOT NULL,

    -- Optional promotional rate (e.g. 0% balance transfer): all five set, or none
    promo_type        VARCHAR(20),
    promo_apr         NUMERIC(5,2),
    promo_balance     NUMERIC(12,2),
    promo_end_date    DATE,
    promo_fee         NUMERIC(12,2),

    FOREIGN KEY (account_id, account_type) REFERENCES account (id, type) ON DELETE CASCADE,
    CONSTRAINT credit_card_promo_all_or_nothing CHECK (
        (promo_type IS NULL AND promo_apr IS NULL AND promo_balance IS NULL
            AND promo_end_date IS NULL AND promo_fee IS NULL)
        OR
        (promo_type IN ('balance_transfer', 'purchase')
            AND promo_apr BETWEEN 0 AND 100
            AND promo_balance > 0
            AND promo_end_date IS NOT NULL
            AND promo_fee >= 0)
    )
);

-- Extra details for type = 'loan'
CREATE TABLE loan_detail (
    account_id      UUID          PRIMARY KEY,
    account_type    VARCHAR(20)   NOT NULL DEFAULT 'loan' CHECK (account_type = 'loan'),
    original_amount NUMERIC(12,2) NOT NULL CHECK (original_amount > 0),
    monthly_payment NUMERIC(12,2) NOT NULL CHECK (monthly_payment > 0),
    payment_day     INTEGER       NOT NULL CHECK (payment_day BETWEEN 1 AND 31),
    apr             NUMERIC(5,2)  NOT NULL DEFAULT 0 CHECK (apr BETWEEN 0 AND 100),

    FOREIGN KEY (account_id, account_type) REFERENCES account (id, type) ON DELETE CASCADE
);
