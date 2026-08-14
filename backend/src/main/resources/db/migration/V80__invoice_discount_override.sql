-- An admin discount that replaces the computed slab rate for one invoice.
--
-- Slabs are banded by invoice value, so a flat promotional rate — 50% off stationery
-- for a season — cannot be expressed as a band without distorting the bands themselves
-- and every other invoice that reads them.
--
-- The override is recorded alongside the invoice rather than applied silently: the rate
-- it replaced is worth seeing on review, and an invoice discounted by hand should be
-- attributable. The rate itself still lands in inv_invoice_lines.applied_discount_pct
-- like any other, so totals, returns and the variance check need no special case.
ALTER TABLE inv_invoices
    ADD COLUMN IF NOT EXISTS discount_override_pct NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS discount_override_by  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS discount_override_at  TIMESTAMP;
