-- One photo covering two payments taken in the same visit.
--
-- A customer settles a bill with two cheques at once. Both are written on the same page
-- of the bill, under one signature, and one photograph shows both. Demanding a separate
-- photo for the second payment does not produce better evidence — it produces the same
-- photograph filed twice, or an accountant photographing a page they have already put
-- away, which is worse than either.
--
-- The URL is copied onto the second payment rather than being read through this column,
-- so every screen that already shows a receipt keeps working untouched. This records
-- where the copy came from, which is what makes it auditable: an admin can see that two
-- payments rest on one photograph, and which one it was taken for.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS receipt_shared_from_payment_id BIGINT;

ALTER TABLE payments DROP CONSTRAINT IF EXISTS fk_payments_receipt_shared_from;
ALTER TABLE payments ADD CONSTRAINT fk_payments_receipt_shared_from
    FOREIGN KEY (receipt_shared_from_payment_id) REFERENCES payments(id);

-- A payment cannot inherit from itself.
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_shared_receipt_not_self;
ALTER TABLE payments ADD CONSTRAINT payments_shared_receipt_not_self
    CHECK (receipt_shared_from_payment_id IS NULL OR receipt_shared_from_payment_id <> id);

CREATE INDEX IF NOT EXISTS idx_payments_receipt_shared_from
    ON payments (receipt_shared_from_payment_id);
