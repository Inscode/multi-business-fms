-- Invoices raised in the invoicing module now flow into the bills section so payments
-- can be collected against them.
--
-- business and bill_source are VARCHAR(50) (since V2), so the new BusinessType.MIX and
-- BillSource.INVOICE values need no schema change.

-- Plastic-only invoices get their own number sequence, alongside mix / rainco / stationery.
CREATE SEQUENCE IF NOT EXISTS seq_inv_pl START WITH 1 INCREMENT BY 1;

-- The bill an invoice raised. Kept on the invoice rather than adding a column to bills:
-- the bills table drives the existing collection workflows and is left untouched.
ALTER TABLE inv_invoices
    ADD COLUMN IF NOT EXISTS bill_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_inv_invoices_bill'
    ) THEN
        ALTER TABLE inv_invoices
            ADD CONSTRAINT fk_inv_invoices_bill
            FOREIGN KEY (bill_id) REFERENCES bills(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_inv_invoices_bill ON inv_invoices (bill_id);

-- The agent invoice number is now compulsory, and it is what the bill is numbered from.
-- Existing rows are left alone; the constraint is enforced in the application so old
-- data stays readable.
