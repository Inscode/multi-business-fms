-- Invoice review: who entered an invoice, under whose name it was billed,
-- and whether the accountant redirected it to a different customer.
--
-- IF NOT EXISTS throughout: some environments already carry a created_by column
-- from an earlier Hibernate auto-create, and ALTER TABLE is atomic — one duplicate
-- would roll back every other column with it.

ALTER TABLE inv_invoices
    ADD COLUMN IF NOT EXISTS billed_name            VARCHAR(255),
    ADD COLUMN IF NOT EXISTS original_customer_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS customer_changed       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS customer_changed_by    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS source                 VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS created_by             VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reviewed               BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reviewed_by            VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reviewed_at            TIMESTAMP;

-- printed_by has been carrying double duty as "who created it" until now.
UPDATE inv_invoices SET created_by = printed_by WHERE created_by IS NULL;

-- Everything that already exists predates the review queue. Marking it reviewed
-- keeps the admin's queue to invoices entered from here on, instead of opening
-- with a backlog nobody was ever asked to check.
UPDATE inv_invoices SET reviewed = TRUE;

CREATE INDEX IF NOT EXISTS idx_inv_invoices_review ON inv_invoices (reviewed, created_at DESC);
