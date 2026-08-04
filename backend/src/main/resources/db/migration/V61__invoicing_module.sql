-- ============================================================
-- V61: Invoicing module (ported from ghanim-wholesale)
-- Runs alongside the existing bills module — all tables are
-- inv_-prefixed and customers gains the two wholesale fields.
-- ============================================================

-- Invoice number sequences (per method)
CREATE SEQUENCE IF NOT EXISTS seq_inv_mix START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_inv_rc  START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_inv_st  START WITH 1 INCREMENT BY 1;

CREATE TABLE inv_settings (
    id          BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    value       VARCHAR(500) NOT NULL
);

INSERT INTO inv_settings (setting_key, value) VALUES ('rainco_cash_discount_pct', '5');

CREATE TABLE inv_brands (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    brand_code         VARCHAR(50),
    category           VARCHAR(30)  NOT NULL,  -- RAINCO | STATIONERY | PLASTIC
    principal          VARCHAR(30)  NOT NULL,  -- RAINCO | STATIONERY_AGENT | OWN
    discount_type      VARCHAR(20)  NOT NULL,  -- SLAB | NONE
    default_margin_pct NUMERIC(5,2),
    active             BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE inv_discount_slabs (
    id           BIGSERIAL PRIMARY KEY,
    brand_id     BIGINT NOT NULL REFERENCES inv_brands(id),
    min_value    NUMERIC(14,2),
    max_value    NUMERIC(14,2),
    discount_pct NUMERIC(5,2) NOT NULL,
    sort_order   INT
);

CREATE TABLE inv_items (
    id                 BIGSERIAL PRIMARY KEY,
    item_code          VARCHAR(50)  NOT NULL UNIQUE,
    description        VARCHAR(300) NOT NULL,
    category           VARCHAR(30)  NOT NULL,
    brand_id           BIGINT NOT NULL REFERENCES inv_brands(id),
    mrp                NUMERIC(12,2),
    margin_pct         NUMERIC(5,2),
    wholesale_price    NUMERIC(12,2),
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    stock_qty          INT NOT NULL DEFAULT 0,
    free_issue_buy_qty INT,
    free_issue_free_qty INT
);

CREATE TABLE inv_invoices (
    id                      BIGSERIAL PRIMARY KEY,
    invoice_no              VARCHAR(20)  NOT NULL UNIQUE,
    external_ref            VARCHAR(50),
    method                  VARCHAR(20)  NOT NULL,  -- MIX | RAINCO_ONLY | STATIONERY_ONLY
    invoice_date            DATE         NOT NULL,
    customer_id             BIGINT NOT NULL REFERENCES customers(id),
    invoice_type            VARCHAR(10)  NOT NULL,  -- CASH | CREDIT
    cash_discount_pct       NUMERIC(5,2),
    plastic_discount_pct    NUMERIC(5,2),
    plastic_discount_amount NUMERIC(12,2),
    agent_printed_net       NUMERIC(12,2),
    printed_by              VARCHAR(100),
    is_duplicate_print      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_inv_external_ref_method UNIQUE (external_ref, method)
);

CREATE TABLE inv_invoice_lines (
    id                   BIGSERIAL PRIMARY KEY,
    invoice_id           BIGINT NOT NULL REFERENCES inv_invoices(id) ON DELETE CASCADE,
    item_id              BIGINT NOT NULL REFERENCES inv_items(id),
    brand_id             BIGINT NOT NULL REFERENCES inv_brands(id),
    qty                  INT           NOT NULL,
    mrp                  NUMERIC(12,2) NOT NULL,
    margin_pct           NUMERIC(5,2),
    wsp                  NUMERIC(12,2) NOT NULL,
    value                NUMERIC(14,2) NOT NULL,
    applied_discount_pct NUMERIC(5,2),
    sort_order           INT
);

CREATE TABLE inv_stock_movements (
    id             BIGSERIAL PRIMARY KEY,
    item_id        BIGINT NOT NULL REFERENCES inv_items(id),
    type           VARCHAR(30) NOT NULL,
    quantity       INT NOT NULL,          -- positive = in, negative = out
    reference_id   BIGINT,
    reference_type VARCHAR(50),
    notes          TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inv_items_category   ON inv_items(category);
CREATE INDEX idx_inv_invoices_date    ON inv_invoices(invoice_date);
CREATE INDEX idx_inv_invoices_customer ON inv_invoices(customer_id);
CREATE INDEX idx_inv_lines_invoice    ON inv_invoice_lines(invoice_id);
CREATE INDEX idx_inv_movements_item   ON inv_stock_movements(item_id);

-- Wholesale fields on the shared customers table (Ventura import resolves by code first)
ALTER TABLE customers ADD COLUMN customer_code VARCHAR(50) UNIQUE;
ALTER TABLE customers ADD COLUMN address VARCHAR(500);
