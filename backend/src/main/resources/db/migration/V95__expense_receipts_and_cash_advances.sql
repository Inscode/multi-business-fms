ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS receipt_image_url TEXT,
    ADD COLUMN IF NOT EXISTS receipt_uploaded_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS advance_id BIGINT;
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS bus_fare_id BIGINT;

CREATE TABLE IF NOT EXISTS expense_bus_fares (
    id BIGSERIAL PRIMARY KEY,
    from_location VARCHAR(120) NOT NULL,
    to_location VARCHAR(120) NOT NULL,
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_bus_fares_route
    ON expense_bus_fares (UPPER(from_location), UPPER(to_location));

CREATE TABLE IF NOT EXISTS expense_advances (
    id BIGSERIAL PRIMARY KEY,
    business VARCHAR(50) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    delivery_run_id BIGINT REFERENCES delivery_runs(id),
    collector_id BIGINT REFERENCES workers(id),
    activity_date DATE NOT NULL,
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    returned_amount DECIMAL(15,2),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    notes VARCHAR(500),
    issued_by_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    settled_at TIMESTAMP,
    CONSTRAINT expense_advances_target_check CHECK (
        (target_type = 'DELIVERY_ROUTE' AND delivery_run_id IS NOT NULL AND collector_id IS NULL)
        OR (target_type = 'COLLECTION_DAY' AND collector_id IS NOT NULL AND delivery_run_id IS NULL)
    ),
    CONSTRAINT expense_advances_status_check CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_expense_advances_open ON expense_advances(status, activity_date);

ALTER TABLE expenses DROP CONSTRAINT IF EXISTS fk_expenses_advance;
ALTER TABLE expenses ADD CONSTRAINT fk_expenses_advance
    FOREIGN KEY (advance_id) REFERENCES expense_advances(id);
ALTER TABLE expenses DROP CONSTRAINT IF EXISTS fk_expenses_bus_fare;
ALTER TABLE expenses ADD CONSTRAINT fk_expenses_bus_fare
    FOREIGN KEY (bus_fare_id) REFERENCES expense_bus_fares(id);

