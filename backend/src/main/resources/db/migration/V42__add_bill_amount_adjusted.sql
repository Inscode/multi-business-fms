ALTER TABLE bill_returns ADD COLUMN bill_amount_adjusted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE summary_load_bill_products ADD COLUMN IF NOT EXISTS unit_price NUMERIC(19,2);
ALTER TABLE summary_load_bill_products ADD COLUMN IF NOT EXISTS line_total NUMERIC(19,2);
