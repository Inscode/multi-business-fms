-- Link workers to user accounts
ALTER TABLE workers ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id);

-- Worker bill visit tracking (one per bill+worker pair, updated in place)
CREATE TABLE IF NOT EXISTS worker_bill_visits (
    id          BIGSERIAL PRIMARY KEY,
    bill_id     BIGINT NOT NULL REFERENCES bills(id),
    worker_id   BIGINT NOT NULL REFERENCES workers(id),
    visit_status VARCHAR(30) NOT NULL DEFAULT 'NOT_VISITED',
    worker_note TEXT,
    visited_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    UNIQUE(bill_id, worker_id)
);

-- Worker payment groups (combined cheque across multiple bills)
CREATE TABLE IF NOT EXISTS worker_payment_groups (
    id           BIGSERIAL PRIMARY KEY,
    worker_id    BIGINT NOT NULL REFERENCES workers(id),
    payment_type VARCHAR(10) NOT NULL,
    cheque_number VARCHAR(50),
    bank_name    VARCHAR(100),
    branch_name  VARCHAR(100),
    total_amount DECIMAL(12,2) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    worker_note  TEXT,
    created_at   TIMESTAMP NOT NULL
);

-- Worker payment entries (individual, optionally linked to a group)
CREATE TABLE IF NOT EXISTS worker_payment_entries (
    id              BIGSERIAL PRIMARY KEY,
    bill_id         BIGINT NOT NULL REFERENCES bills(id),
    worker_id       BIGINT NOT NULL REFERENCES workers(id),
    group_id        BIGINT REFERENCES worker_payment_groups(id),
    amount          DECIMAL(12,2) NOT NULL,
    payment_type    VARCHAR(10) NOT NULL,
    cheque_number   VARCHAR(50),
    bank_name       VARCHAR(100),
    branch_name     VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    worker_note     TEXT,
    entered_at      TIMESTAMP NOT NULL,
    confirmed_at    TIMESTAMP,
    confirmed_by_id BIGINT REFERENCES users(id),
    rejected_reason TEXT,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wpe_bill_id   ON worker_payment_entries(bill_id);
CREATE INDEX IF NOT EXISTS idx_wpe_worker_id ON worker_payment_entries(worker_id);
CREATE INDEX IF NOT EXISTS idx_wpe_status    ON worker_payment_entries(status);
CREATE INDEX IF NOT EXISTS idx_wbv_worker_id ON worker_bill_visits(worker_id);
