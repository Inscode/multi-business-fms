-- Add customer_id FK to bills (nullable — existing bills backfilled below)
ALTER TABLE bills ADD COLUMN IF NOT EXISTS customer_id BIGINT;
ALTER TABLE bills DROP CONSTRAINT IF EXISTS fk_bill_customer;
ALTER TABLE bills ADD CONSTRAINT fk_bill_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id);

-- Backfill: match existing bills to customers by exact name (case-insensitive)
UPDATE bills b
SET customer_id = c.id
FROM customers c
WHERE TRIM(UPPER(b.customer_name)) = TRIM(UPPER(c.name))
  AND b.customer_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_bills_customer_id ON bills (customer_id);
