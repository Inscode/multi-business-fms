-- ============================================================
-- V67: Forward cash-flow forecast.
--
-- Money out: a GRN is payable N days after it is received (typed by the
-- admin on the note), plus obligations that pre-date this system — the
-- cheques already issued against old GRNs and opening stock, which exist
-- nowhere else and would otherwise make the forecast look far too healthy.
--
-- Money in comes from cheques already held (payments.cheque_date), so
-- nothing new is stored for the inflow side.
-- ============================================================

-- Credit period on a goods received note
ALTER TABLE inv_grns ADD COLUMN payment_terms_days INT;
ALTER TABLE inv_grns ADD COLUMN due_date DATE;

-- Opening-stock notes record what is on the shelf, not a debt to the principal,
-- so they must never appear in the forecast as money going out.
ALTER TABLE inv_grns ADD COLUMN payment_required BOOLEAN NOT NULL DEFAULT TRUE;

-- Obligations not represented by a GRN in this system: cheques already
-- written for older purchases, and opening-stock liabilities.
CREATE TABLE supplier_payables (
    id             BIGSERIAL PRIMARY KEY,
    business       VARCHAR(50)  NOT NULL,   -- RAINCO | STATIONERY | PLASTIC | ...
    supplier_name  VARCHAR(200),
    description    VARCHAR(300) NOT NULL,
    amount         NUMERIC(14,2) NOT NULL,
    due_date       DATE         NOT NULL,
    cheque_number  VARCHAR(50),
    bank_name      VARCHAR(100),
    settled        BOOLEAN      NOT NULL DEFAULT FALSE,
    settled_on     DATE,
    notes          TEXT,
    created_by_id  BIGINT REFERENCES users(id),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_supplier_payables_due      ON supplier_payables(due_date);
CREATE INDEX idx_supplier_payables_business ON supplier_payables(business);
CREATE INDEX idx_inv_grns_due_date          ON inv_grns(due_date);
