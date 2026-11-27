-- Deferred-interest promos (common on store cards): if any of the promo balance
-- is left on the end date, interest is charged back to the start of the promo.
-- The payoff plan treats these end dates as hard deadlines.
ALTER TABLE credit_card_detail
    ADD COLUMN promo_deferred_interest BOOLEAN;

-- Existing promos are regular ones; cards without a promo keep NULL like the other promo_* columns.
UPDATE credit_card_detail SET promo_deferred_interest = FALSE WHERE promo_type IS NOT NULL;
