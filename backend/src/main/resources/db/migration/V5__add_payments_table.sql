ALTER TABLE bills ADD COLUMN amount_paid DECIMAL(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE bills ADD COLUMN balance_remaining DECIMAL(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE bills ADD COLUMN fully_paid BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE payments (
    id              BIGSERIAL     PRIMARY KEY,
    bill_id         BIGINT        NOT NULL REFERENCES bills(id),
    amount          DECIMAL(12,2) NOT NULL,
    payment_type    VARCHAR(50)   NOT NULL,
    status          VARCHAR(50)   NOT NULL DEFAULT 'PENDING_CONFIRMATION',
    entered_by      BIGINT        NOT NULL REFERENCES users(id),
    confirmed_by    BIGINT        REFERENCES users(id),
    confirmed_at    TIMESTAMP,
    payment_date    DATE          NOT NULL DEFAULT CURRENT_DATE,
    notes           TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_bill_id ON payments(bill_id);
CREATE INDEX idx_payments_status  ON payments(status);
CREATE INDEX idx_payments_date    ON payments(payment_date);