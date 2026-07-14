-- Rename any duplicate bill_numbers (keep the lowest id, suffix others with -DUP-{id})
UPDATE bills
SET bill_number = bill_number || '-DUP-' || CAST(id AS TEXT)
WHERE id NOT IN (
    SELECT MIN(id) FROM bills GROUP BY bill_number
);

-- Add unique constraint
ALTER TABLE bills ADD CONSTRAINT uk_bills_bill_number UNIQUE (bill_number);
