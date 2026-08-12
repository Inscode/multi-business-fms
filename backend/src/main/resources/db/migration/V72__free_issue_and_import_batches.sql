-- Manually entered free issue, admin editing, and import batches.
--
-- Distinct from the automatic buy-N-get-M scheme on inv_items, which is computed at
-- print time only and never touches stock. This one is typed per line, stored, and
-- deducted — free goods leave the warehouse like anything else.

ALTER TABLE inv_invoice_lines
    ADD COLUMN IF NOT EXISTS free_qty INT NOT NULL DEFAULT 0;

-- A paid quantity of zero is now legitimate: a free-only line, such as the K01047
-- umbrella issued against a stationery invoice.
ALTER TABLE inv_invoice_lines ALTER COLUMN qty DROP NOT NULL;
UPDATE inv_invoice_lines SET qty = 0 WHERE qty IS NULL;
ALTER TABLE inv_invoice_lines ALTER COLUMN qty SET NOT NULL;

ALTER TABLE inv_invoices
    ADD COLUMN IF NOT EXISTS free_issue_added_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS free_issue_added_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS edited_by            VARCHAR(100),
    ADD COLUMN IF NOT EXISTS edited_at            TIMESTAMP,
    ADD COLUMN IF NOT EXISTS import_batch_id      BIGINT;

-- One row per Import press, so a batch can be checked against the agent's summary bill.
CREATE TABLE IF NOT EXISTS inv_import_batches (
    id            BIGSERIAL PRIMARY KEY,
    category      VARCHAR(30)  NOT NULL,
    file_name     VARCHAR(255),
    imported_by   VARCHAR(100),
    imported_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    invoice_count INT          NOT NULL DEFAULT 0
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inv_invoices_batch') THEN
        ALTER TABLE inv_invoices
            ADD CONSTRAINT fk_inv_invoices_batch
            FOREIGN KEY (import_batch_id) REFERENCES inv_import_batches(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_inv_invoices_batch ON inv_invoices (import_batch_id);
CREATE INDEX IF NOT EXISTS idx_inv_batches_date   ON inv_import_batches (imported_at DESC);
