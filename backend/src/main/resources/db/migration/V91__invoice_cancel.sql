-- Cancelling an invoice, the same way a bill is cancelled.
--
-- An invoice raised for goods that never went out has to be voidable without being
-- erased: the number was issued, the stock moved, and both facts need somewhere to
-- live. Deleting it would remove the record of a mistake that was made, which is the
-- one thing a reconciliation later will be looking for.
--
-- Deleting stays available for the other case — an invoice keyed in error that never
-- corresponded to anything — and removes it outright.
ALTER TABLE inv_invoices
    ADD COLUMN IF NOT EXISTS cancelled     BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(300),
    ADD COLUMN IF NOT EXISTS cancelled_by  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cancelled_at  TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_invoices_cancelled ON inv_invoices (cancelled);
