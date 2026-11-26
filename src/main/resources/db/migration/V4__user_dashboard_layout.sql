-- The order of the dashboard's sections, chosen by the user, e.g.
-- "upcoming,cards,-promotions,subscriptions,accounts,loans".
-- A leading "-" means hidden. NULL means the default order.
ALTER TABLE app_user ADD COLUMN dashboard_sections VARCHAR(300);
