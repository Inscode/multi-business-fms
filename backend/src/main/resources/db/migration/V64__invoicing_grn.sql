-- ============================================================
-- V64: Goods received notes for the invoicing module.
-- Accountants and admins record incoming stock; stock levels only
-- move when an admin approves, so a pending GRN never inflates
-- inventory. Each note is category-wise — every line must be an
-- item of the note's category.
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS seq_inv_grn START WITH 1 INCREMENT BY 1;

CREATE TABLE inv_grns (
    id               BIGSERIAL PRIMARY KEY,
    grn_no           VARCHAR(30)  NOT NULL UNIQUE,
    category         VARCHAR(30)  NOT NULL,   -- RAINCO | STATIONERY | PLASTIC
    supplier_name    VARCHAR(200),
    received_date    DATE         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED
    rejection_reason TEXT,
    notes            TEXT,
    submitted_by     VARCHAR(100),
    reviewed_by      VARCHAR(100),
    reviewed_at      TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE inv_grn_lines (
    id         BIGSERIAL PRIMARY KEY,
    grn_id     BIGINT NOT NULL REFERENCES inv_grns(id) ON DELETE CASCADE,
    item_id    BIGINT NOT NULL REFERENCES inv_items(id),
    qty        INT NOT NULL,
    unit_cost  NUMERIC(12,2),
    line_total NUMERIC(14,2)
);

CREATE INDEX idx_inv_grns_status    ON inv_grns(status);
CREATE INDEX idx_inv_grn_lines_grn  ON inv_grn_lines(grn_id);
