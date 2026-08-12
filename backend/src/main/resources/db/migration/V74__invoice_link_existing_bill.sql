-- Some bills were entered straight into the bills section without their stock being
-- reduced. Entering those as invoices now must move the stock without raising a second
-- bill for money already being collected.
--
-- When an invoice's number already belongs to a bill in the same business, the invoice
-- attaches to that bill instead of creating one. The flag records that this happened, so
-- it is visible on review rather than silent.

ALTER TABLE inv_invoices
    ADD COLUMN IF NOT EXISTS bill_linked_existing BOOLEAN NOT NULL DEFAULT FALSE;
