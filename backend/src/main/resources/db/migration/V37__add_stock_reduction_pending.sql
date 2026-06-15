-- Add stock_cleared flag to bills (for historical bills with no stock movement)
ALTER TABLE bills ADD COLUMN IF NOT EXISTS stock_cleared BOOLEAN NOT NULL DEFAULT FALSE;

-- Pending individual stock reduction requests (require admin approval)
CREATE TABLE IF NOT EXISTS stock_reduction_pending (
    id                BIGSERIAL PRIMARY KEY,
    bill_id           BIGINT       NOT NULL REFERENCES bills(id),
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    submitted_by_id   BIGINT       REFERENCES users(id),
    reviewed_by_id    BIGINT       REFERENCES users(id),
    submitted_at      TIMESTAMP,
    reviewed_at       TIMESTAMP,
    notes             TEXT,
    rejection_reason  TEXT
);

-- Items attached to each pending reduction request
CREATE TABLE IF NOT EXISTS stock_reduction_pending_items (
    id            BIGSERIAL PRIMARY KEY,
    reduction_id  BIGINT          NOT NULL REFERENCES stock_reduction_pending(id) ON DELETE CASCADE,
    product_id    BIGINT          NOT NULL REFERENCES return_products(id),
    quantity      BIGINT          NOT NULL,
    unit_price    NUMERIC(15, 2)  NOT NULL,
    line_total    NUMERIC(15, 2)  NOT NULL,
    created_at    TIMESTAMP
);
