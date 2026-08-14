-- Returns rework: salable + damage returns that reduce what a bill is worth without
-- destroying its invoiced amount, with an accountant goods-received gate before payment
-- and an admin reversal that puts the money back exactly.

-- ── Bills carry their returns alongside the invoiced total ───────────────────
-- total_amount stays what was invoiced. Payable = total_amount - returns_total.
-- Reversing a return then costs nothing more than recomputing this one number.
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS returns_total NUMERIC(14,2) NOT NULL DEFAULT 0;

-- ── Return header ────────────────────────────────────────────────────────────
ALTER TABLE bill_returns
    -- Items taken off the bill's own invoice lines, versus picked from the catalogue
    -- because the goods came off an older bill.
    ADD COLUMN IF NOT EXISTS from_same_bill        BOOLEAN NOT NULL DEFAULT FALSE,
    -- The goods were sold on a cash invoice, so the 5% they were discounted at
    -- purchase comes back off the credit.
    ADD COLUMN IF NOT EXISTS cash_sale             BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cash_discount_pct     NUMERIC(5,2),

    -- Accountant's confirmation that the goods physically arrived. Payment is blocked
    -- until this is answered, so a return can't be forgotten at the moment cash moves.
    ADD COLUMN IF NOT EXISTS goods_receipt         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS goods_confirmed_by_id BIGINT,
    ADD COLUMN IF NOT EXISTS goods_confirmed_at    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS goods_confirmed_note  VARCHAR(500),

    -- Admin reversal — the accountant made a mistake, put it all back.
    ADD COLUMN IF NOT EXISTS cancelled_by_id       BIGINT,
    ADD COLUMN IF NOT EXISTS cancelled_at          TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancel_reason         VARCHAR(500),

    -- Stock has been moved for this return; guards against moving it twice.
    ADD COLUMN IF NOT EXISTS stock_applied         BOOLEAN NOT NULL DEFAULT FALSE,

    -- Any line amount was overridden by hand — surfaced to the admin on review.
    ADD COLUMN IF NOT EXISTS amount_edited         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS amount_edited_by      VARCHAR(100),

    -- Returns approved before this migration already had their deduction written
    -- destructively into bills.total_amount. They must not be counted again in
    -- returns_total, so they are flagged and left alone.
    ADD COLUMN IF NOT EXISTS legacy_amount_adjusted BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE bill_returns
   SET legacy_amount_adjusted = TRUE
 WHERE bill_amount_adjusted = TRUE;

ALTER TABLE bill_returns DROP CONSTRAINT IF EXISTS fk_bill_returns_goods_confirmed_by;
ALTER TABLE bill_returns ADD CONSTRAINT fk_bill_returns_goods_confirmed_by
    FOREIGN KEY (goods_confirmed_by_id) REFERENCES users(id);

ALTER TABLE bill_returns DROP CONSTRAINT IF EXISTS fk_bill_returns_cancelled_by;
ALTER TABLE bill_returns ADD CONSTRAINT fk_bill_returns_cancelled_by
    FOREIGN KEY (cancelled_by_id) REFERENCES users(id);

-- ── Return lines ─────────────────────────────────────────────────────────────
ALTER TABLE bill_return_items
    -- The invoice line these goods were sold on. Present for a same-bill return, and
    -- the reason its credit needs no guesswork: wsp and slab % are read straight off it.
    ADD COLUMN IF NOT EXISTS invoice_line_id       BIGINT,
    -- The invoicing item, so stock can be moved. The legacy product_id points at the
    -- old return_products catalogue and cannot.
    ADD COLUMN IF NOT EXISTS inv_item_id           BIGINT,

    ADD COLUMN IF NOT EXISTS gross_value           NUMERIC(14,2),
    ADD COLUMN IF NOT EXISTS slab_discount_pct     NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS cash_discount_pct     NUMERIC(5,2),
    -- What the customer is credited for this line, after both discounts.
    ADD COLUMN IF NOT EXISTS credit_amount         NUMERIC(14,2),
    -- Set when someone typed over the computed credit. The computed figure is kept
    -- so the admin can see what it would have been.
    ADD COLUMN IF NOT EXISTS amount_edited         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS computed_credit_amount NUMERIC(14,2);

UPDATE bill_return_items
   SET gross_value   = COALESCE(gross_value, line_total),
       credit_amount = COALESCE(credit_amount, line_total);

ALTER TABLE bill_return_items DROP CONSTRAINT IF EXISTS fk_bri_invoice_line;
ALTER TABLE bill_return_items ADD CONSTRAINT fk_bri_invoice_line
    FOREIGN KEY (invoice_line_id) REFERENCES inv_invoice_lines(id);

ALTER TABLE bill_return_items DROP CONSTRAINT IF EXISTS fk_bri_inv_item;
ALTER TABLE bill_return_items ADD CONSTRAINT fk_bri_inv_item
    FOREIGN KEY (inv_item_id) REFERENCES inv_items(id);

-- ── Lifecycle ────────────────────────────────────────────────────────────────
-- PENDING stays "submitted" and APPROVED stays "reviewed and deducted", so existing
-- rows and the queries over them keep their meaning. The new values sit between and
-- beside them: GOODS_CONFIRMED (accountant saw the goods), NOT_RECEIVED (they never
-- came), CANCELLED (admin reversed it).
ALTER TABLE bill_returns DROP CONSTRAINT IF EXISTS bill_returns_status_check;
ALTER TABLE bill_returns ADD CONSTRAINT bill_returns_status_check
    CHECK (status IN ('PENDING','GOODS_CONFIRMED','APPROVED','REJECTED',
                      'NOT_RECEIVED','CANCELLED'));

ALTER TABLE bill_returns DROP CONSTRAINT IF EXISTS bill_returns_goods_receipt_check;
ALTER TABLE bill_returns ADD CONSTRAINT bill_returns_goods_receipt_check
    CHECK (goods_receipt IS NULL OR goods_receipt IN ('ALL','PARTIAL','NONE'));

CREATE INDEX IF NOT EXISTS idx_bill_returns_bill_status ON bill_returns (bill_id, status);
