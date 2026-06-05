-- ============================================================
-- V31: Stock module enhancements
-- Changes:
--   1. bills.will_be_linked  — flag for linking system bills
--   2. summary_load_bill_items join table rename guard
--   3. stock_in_requests / stock_in_request_items (if missing)
--   4. summary_load_bill_products (if missing)
-- ============================================================

-- ── 1. Add will_be_linked column to bills ────────────────────
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS will_be_linked BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN bills.will_be_linked IS
    'TRUE for SYSTEM bills that will be reconciled via End-of-Month linking. '
    'Stock is covered by linked DRAFT/MANUAL children — no separate BILL_OUT movement created.';

CREATE INDEX IF NOT EXISTS idx_bills_will_be_linked ON bills (will_be_linked)
    WHERE will_be_linked = TRUE;

-- ── 2. summary_load_bill_products (safe re-create guard) ─────
-- Already created in V19; included here for server deployments
-- that might have missed earlier migrations.
CREATE TABLE IF NOT EXISTS summary_load_bill_products (
    id                   BIGSERIAL PRIMARY KEY,
    summary_load_bill_id BIGINT    NOT NULL,
    product_id           BIGINT    NOT NULL,
    quantity             BIGINT    NOT NULL,
    created_at           TIMESTAMP NOT NULL,
    FOREIGN KEY (summary_load_bill_id) REFERENCES summary_load_bills(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)           REFERENCES return_products(id)
);
CREATE INDEX IF NOT EXISTS idx_slbp_summary_id ON summary_load_bill_products (summary_load_bill_id);
CREATE INDEX IF NOT EXISTS idx_slbp_product_id ON summary_load_bill_products (product_id);

-- ── 3. stock_in_requests (safe re-create guard) ───────────────
CREATE TABLE IF NOT EXISTS stock_in_requests (
    id               BIGSERIAL PRIMARY KEY,
    reference_number VARCHAR(100),
    stock_date       DATE        NOT NULL,
    notes            TEXT,
    status           VARCHAR(50) NOT NULL,
    submitted_by     BIGINT      NOT NULL,
    approved_by      BIGINT,
    approved_at      TIMESTAMP,
    rejection_reason VARCHAR(500),
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP,
    FOREIGN KEY (submitted_by) REFERENCES users(id),
    FOREIGN KEY (approved_by)  REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_sir_status     ON stock_in_requests (status);
CREATE INDEX IF NOT EXISTS idx_sir_stock_date ON stock_in_requests (stock_date);

-- ── 4. stock_in_request_items (safe re-create guard) ─────────
CREATE TABLE IF NOT EXISTS stock_in_request_items (
    id                  BIGSERIAL PRIMARY KEY,
    stock_in_request_id BIGINT    NOT NULL,
    product_id          BIGINT    NOT NULL,
    quantity            BIGINT    NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    FOREIGN KEY (stock_in_request_id) REFERENCES stock_in_requests(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)          REFERENCES return_products(id)
);
CREATE INDEX IF NOT EXISTS idx_siri_request_id ON stock_in_request_items (stock_in_request_id);
CREATE INDEX IF NOT EXISTS idx_siri_product_id ON stock_in_request_items (product_id);
