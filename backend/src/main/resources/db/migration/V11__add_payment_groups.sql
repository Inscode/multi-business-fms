CREATE TABLE payment_groups (
    id               BIGSERIAL      PRIMARY KEY,
    payment_type     VARCHAR(50)    NOT NULL,
    cheque_number    VARCHAR(100),
    bank_name        VARCHAR(100),
    branch_name      VARCHAR(100),
    reference_number VARCHAR(100),
    cheque_date      DATE,
    payment_date     DATE           NOT NULL,
    total_amount     DECIMAL(15,2)  NOT NULL,
    notes            TEXT,
    status           VARCHAR(50)    NOT NULL DEFAULT 'ENTERED',
    entered_by       BIGINT         NOT NULL REFERENCES users(id),
    confirmed_by     BIGINT         REFERENCES users(id),
    confirmed_at     TIMESTAMP,
    return_reason    TEXT,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP
);

ALTER TABLE payments
    ADD COLUMN group_id BIGINT REFERENCES payment_groups(id);

CREATE INDEX idx_payment_groups_status ON payment_groups(status);
CREATE INDEX idx_payments_group_id     ON payments(group_id);
