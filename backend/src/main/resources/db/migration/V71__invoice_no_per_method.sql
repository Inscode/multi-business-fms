-- Invoice numbers now follow the bills convention (SYS-1247 / MAN-324 / BK-88), and bill
-- numbers are unique per business, not globally: SYS-743 is a legitimate Rainco bill AND a
-- legitimate Stationery bill. A global UNIQUE on invoice_no would reject the second one.
--
-- Scoped to method instead, which maps one-to-one onto business.

ALTER TABLE inv_invoices DROP CONSTRAINT IF EXISTS inv_invoices_invoice_no_key;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_inv_invoice_no_method'
    ) THEN
        ALTER TABLE inv_invoices
            ADD CONSTRAINT uq_inv_invoice_no_method UNIQUE (invoice_no, method);
    END IF;
END $$;

-- SYS-/MAN-/BK- numbers can run longer than the old GD-RC-1 form.
ALTER TABLE inv_invoices ALTER COLUMN invoice_no TYPE VARCHAR(50);
