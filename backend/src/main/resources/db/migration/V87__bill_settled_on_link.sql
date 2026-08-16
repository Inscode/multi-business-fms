-- A system bill collected on a manual bill instead.
--
-- The sale is billed by hand at the shop, and the same sale is entered here to keep the
-- record and move the stock. Only one of the two is collected. Until now the system one
-- was cancelled, which said the sale did not happen — it did, on the other bill.
--
-- Linking says the true thing: this bill is real, and its money is being collected
-- there. So it stops counting as outstanding, drops off the aging report, and closes
-- itself when the bill it points at is paid off.
--
-- Distinct from bill_stock_links, which ties one system bill to MANY manual bills for
-- end-of-month stock reconciliation. This is one-to-one and about the money, not stock.
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS settled_on_bill_id BIGINT,
    ADD COLUMN IF NOT EXISTS settled_on_at      TIMESTAMP,
    ADD COLUMN IF NOT EXISTS settled_on_by      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS settled_on_note    VARCHAR(300);

ALTER TABLE bills DROP CONSTRAINT IF EXISTS fk_bills_settled_on;
ALTER TABLE bills ADD CONSTRAINT fk_bills_settled_on
    FOREIGN KEY (settled_on_bill_id) REFERENCES bills(id);

-- A bill cannot be collected on itself.
ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_settled_on_not_self;
ALTER TABLE bills ADD CONSTRAINT bills_settled_on_not_self
    CHECK (settled_on_bill_id IS NULL OR settled_on_bill_id <> id);

CREATE INDEX IF NOT EXISTS idx_bills_settled_on ON bills (settled_on_bill_id);
